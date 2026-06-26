package sg.edu.nus.iss.wellness.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.client.AiServiceClient;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.ChatMessage;
import sg.edu.nus.iss.wellness.repository.ChatMessageRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.service.CurrentUserService;
import sg.edu.nus.iss.wellness.service.DtoMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Orchestrates chatbot requests through the Python RAG service.
 *
 * @author SA62 Team
 */
@RestController
@RequestMapping("/api/chat/messages")
public class ChatController {
    private final CurrentUserService currentUserService;
    private final WellnessRecordRepository wellnessRecords;
    private final ChatMessageRepository chatMessages;
    private final AiServiceClient aiServiceClient;

    public ChatController(CurrentUserService currentUserService,
                          WellnessRecordRepository wellnessRecords,
                          ChatMessageRepository chatMessages,
                          AiServiceClient aiServiceClient) {
        this.currentUserService = currentUserService;
        this.wellnessRecords = wellnessRecords;
        this.chatMessages = chatMessages;
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping
    public ChatDtos.ChatResponse ask(@Valid @RequestBody ChatDtos.ChatRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        List<ChatDtos.RecentRecord> recentRecords = wellnessRecords
                .findByUserAndRecordDateAfterOrderByRecordDateDesc(user, LocalDate.now().minusDays(14))
                .stream()
                .map(record -> new ChatDtos.RecentRecord(
                        record.getRecordDate().toString(),
                        record.getSleepHours().doubleValue(),
                        record.getExerciseType(),
                        record.getExerciseMinutes(),
                        record.getMoodScore()))
                .toList();
        ChatDtos.AiChatResponse ai = aiServiceClient.chat(new ChatDtos.AiChatRequest(user.getId(), request.question(), recentRecords));

        ChatMessage message = new ChatMessage();
        message.setUser(user);
        message.setUserQuestion(request.question());
        message.setAssistantAnswer(ai.answer());
        message.setModelName(ai.modelName());
        message.setSourceSummary(sourcesToText(ai.sources()));
        return DtoMapper.chat(chatMessages.save(message), ai.sources());
    }

    @GetMapping
    public List<ChatDtos.ChatResponse> list() {
        AppUser user = currentUserService.requireCurrentUser();
        return chatMessages.findByUserOrderByCreatedAtDesc(user).stream()
                .map(message -> DtoMapper.chat(message, List.of()))
                .toList();
    }

    private String sourcesToText(List<ChatDtos.SourceSnippet> sources) {
        if (sources == null) {
            return "";
        }
        return sources.stream()
                .map(source -> source.title() + ": " + source.snippet())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}

