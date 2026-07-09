package sg.edu.nus.iss.wellness.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.iss.wellness.client.AiServiceClient;
import sg.edu.nus.iss.wellness.client.PremiumAiClient;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.error.ApiException;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.ChatMessage;
import sg.edu.nus.iss.wellness.model.Role;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.ChatMessageRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;

/**
 * Business logic for chat orchestration with RAG service.
 * Handles question forwarding, wellness context retrieval, and response persistence.
 *
 * @author Tiong Zhong Cheng, Kumaraguru Surya, Tang Chee Seng
 */

@Service
@Transactional
public class ChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
    private static final int RECENT_DAYS = 14;
    private static final String SOURCE_SEPARATOR = "\n";

    private final AiServiceClient aiServiceClient;
    private final ChatMessageRepository chatMessages;
    private final WellnessRecordRepository wellnessRecords;
    private final AppUserRepository users;
    private final PremiumAiClient premiumAiClient;
    private final ExerciseIntentClassifier intentClassifier;

    public ChatService(AiServiceClient aiServiceClient,
                       ChatMessageRepository chatMessages,
                       WellnessRecordRepository wellnessRecords,
                       AppUserRepository users,
                       PremiumAiClient premiumAiClient,
                       ExerciseIntentClassifier intentClassifier) {
        this.aiServiceClient = aiServiceClient;
        this.chatMessages = chatMessages;
        this.wellnessRecords = wellnessRecords;
        this.users = users;
        this.premiumAiClient = premiumAiClient;
        this.intentClassifier = intentClassifier;
    }

    /**
     * Ask a question and get a response from RAG with recent wellness context.
     *
     * <p>Premium users asking an exercise/weather question (and only when the premium
     * agent is configured) are routed to the local weather agent, passing the user's
     * GPS coordinates so it can pick the nearest station. Any premium failure falls
     * back to the standard RAG chatbot, so the caller always gets an answer.
     *
     * @param user the authenticated user asking the question
     * @param question the wellness question
     * @param latitude optional GPS latitude from the client (null ⇒ national average)
     * @param longitude optional GPS longitude from the client
     * @return chat response with answer and sources
     */
    public ChatDtos.ChatResponse askQuestion(AppUser user, String question,
                                             Double latitude, Double longitude) {
        LOGGER.debug("Processing chat question for user {} : {}", user.getId(), question);

        List<ChatDtos.RecentRecord> recentRecords = fetchRecentWellnessRecords(user);
        ChatDtos.AiChatResponse aiResponse = null;

        if (routeToPremium(user, question)) {
            String context = buildContextString(recentRecords, question);
            String records = formatRecords(recentRecords);
            aiResponse = premiumAiClient.premiumChat(question, context, records, latitude, longitude);
            // premiumChat returns null on any failure (PC unreachable) → fall through to standard.
        }
        if (aiResponse == null) {
            aiResponse = aiServiceClient.chat(
                    new ChatDtos.AiChatRequest(user.getId(), question, recentRecords));
        }

        ChatMessage savedMessage = persistChatMessage(user, question, aiResponse);
        return DtoMapper.chat(savedMessage, aiResponse.sources());
    }

    /**
     * Retrieve chat history for the authenticated user.
     *
     * @param user the authenticated user
     * @return list of chat responses ordered by newest first
     */
    public List<ChatDtos.ChatResponse> getChatHistory(AppUser user) {
        LOGGER.debug("Retrieving chat history for user {}", user.getId());
        return chatMessages.findByUserOrderByCreatedAtDesc(user).stream()
                .map(message -> DtoMapper.chat(message, List.of()))
                .toList();
    }

    /**
     * Fetch recent wellness records for AI context. Exposed for the streaming chat path,
     * which resolves this context on the request thread before handing off to a background
     * worker so no JPA session is touched from the async stream.
     *
     * @param user the user whose records to fetch
     * @return list of recent wellness records
     */
    @Transactional(readOnly = true)
    public List<ChatDtos.RecentRecord> fetchRecentWellnessRecords(AppUser user) {
        return loadRecentWellnessRecords(user);
    }

    /**
     * Shared record-loading logic used by both the transactional entry point above and the
     * synchronous chat path. Kept private so internal calls do not self-invoke the proxied
     * {@code @Transactional} method (which would bypass Spring's transaction advice).
     */
    private List<ChatDtos.RecentRecord> loadRecentWellnessRecords(AppUser user) {
        return wellnessRecords
                .findByUserAndRecordDateAfterOrderByRecordDateDesc(
                        user,
                        LocalDate.now(ZoneId.systemDefault()).minusDays(RECENT_DAYS)
                )
                .stream()
                .map(rec -> new ChatDtos.RecentRecord(
                        rec.getRecordDate().toString(),
                        rec.getSleepHours().doubleValue(),
                        rec.getExerciseType(),
                        rec.getExerciseMinutes(),
                        rec.getMoodScore()
                ))
                .toList();
    }

    /**
     * Persist chat exchange to database.
     *
     * @param user the user
     * @param question the user's question
     * @param aiResponse the AI service response
     * @return saved chat message
     */
    private ChatMessage persistChatMessage(AppUser user, String question, ChatDtos.AiChatResponse aiResponse) {
        ChatMessage message = new ChatMessage();
        message.setUser(user);
        message.setUserQuestion(question);
        message.setAssistantAnswer(aiResponse.answer());
        message.setModelName(aiResponse.modelName());
        message.setSourceSummary(formatSources(aiResponse.sources()));
        return chatMessages.save(message);
    }

    /** Persist a completed streamed chat exchange. */
    @Transactional
    public ChatDtos.ChatResponse saveStreamedAnswer(Long userId,
                                                    String question,
                                                    String answer,
                                                    String modelName,
                                                    List<ChatDtos.SourceSnippet> sources) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        ChatMessage message = new ChatMessage();
        message.setUser(user);
        message.setUserQuestion(question);
        message.setAssistantAnswer(answer);
        message.setModelName(modelName);
        message.setSourceSummary(formatSources(sources));
        ChatMessage saved = chatMessages.save(message);
        return DtoMapper.chat(saved, sources);
    }

    /**
     * Format source snippets into a readable string.
     *
     * @param sources the source snippets from RAG
     * @return formatted source summary
     */
    private String formatSources(List<ChatDtos.SourceSnippet> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        return sources.stream()
                .map(source -> source.title() + ": " + source.snippet())
                .toList()
                .stream()
                .reduce((left, right) -> left + SOURCE_SEPARATOR + right)
                .orElse("");
    }

    boolean routeToPremium(AppUser user, String question) {
        return user.getRole() == Role.PREMIUM_USER
                && premiumAiClient.isEnabled()
                && intentClassifier.isExerciseRelated(question);
    }

    String buildContextString(List<ChatDtos.RecentRecord> records, String question) {
        try {
            ChatDtos.AiChatResponse std = aiServiceClient.chat(
                    new ChatDtos.AiChatRequest(0L, question, records));
            if (std.sources() != null) {
                return std.sources().stream()
                        .map(s -> "Source: " + s.title() + "\n" + s.snippet())
                        .reduce((a, b) -> a + "\n\n" + b).orElse("");
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not pre-fetch RAG context for premium: {}", ex.getMessage());
        }
        return "";
    }

    String formatRecords(List<ChatDtos.RecentRecord> records) {
        if (records == null || records.isEmpty()) {
            return "No recent wellness records.";
        }
        return records.stream()
                .map(r -> String.format("- %s: sleep %.1fh, exercise %s %dmin, mood %d/5",
                        r.recordDate(), r.sleepHours(),
                        r.exerciseType() != null ? r.exerciseType() : "none",
                        r.exerciseMinutes(), r.moodScore()))
                .reduce((a, b) -> a + "\n" + b).orElse("No recent wellness records.");
    }
}
