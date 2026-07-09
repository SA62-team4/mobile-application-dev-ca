package sg.edu.nus.iss.wellness.service;

import sg.edu.nus.iss.wellness.dto.AuthDtos;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.dto.RecommendationDtos;
import sg.edu.nus.iss.wellness.dto.WellnessDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.ChatMessage;
import sg.edu.nus.iss.wellness.model.Recommendation;
import sg.edu.nus.iss.wellness.model.WellnessRecord;

import java.util.Arrays;
import java.util.List;

/**
 * Converts JPA entities to API DTOs.
 *
 * @author Tiong Zhong Cheng
 */
public final class DtoMapper {
    private DtoMapper() {
    }

    public static AuthDtos.UserResponse user(AppUser user) {
        return new AuthDtos.UserResponse(user.getId(), user.getDisplayName(), user.getEmail());
    }

    public static WellnessDtos.WellnessRecordResponse wellness(WellnessRecord wellnessRecord) {
        return new WellnessDtos.WellnessRecordResponse(
                wellnessRecord.getId(),
                wellnessRecord.getRecordDate(),
                wellnessRecord.getSleepHours(),
            wellnessRecord.getWeightKg(),
                wellnessRecord.getExerciseType(),
                wellnessRecord.getExerciseMinutes(),
                wellnessRecord.getMoodScore(),
                wellnessRecord.getNotes(),
                wellnessRecord.getCreatedAt(),
                wellnessRecord.getUpdatedAt()
        );
    }

    public static ChatDtos.ChatResponse chat(ChatMessage message, List<ChatDtos.SourceSnippet> sources) {
        return new ChatDtos.ChatResponse(
                message.getId(),
                message.getUserQuestion(),
                message.getAssistantAnswer(),
                sources,
                message.getModelName(),
                message.getCreatedAt()
        );
    }

    public static RecommendationDtos.RecommendationResponse recommendation(Recommendation recommendation) {
        return new RecommendationDtos.RecommendationResponse(
                recommendation.getId(),
                recommendation.getTitle(),
                recommendation.getTrendSummary(),
                recommendation.getRecommendationText(),
                splitActionItems(recommendation.getActionItems()),
                recommendation.getGeneratedBy(),
                recommendation.getCreatedAt()
        );
    }

    public static List<String> splitActionItems(String actionItems) {
        if (actionItems == null || actionItems.isBlank()) {
            return List.of();
        }
        return Arrays.stream(actionItems.split("\\n"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    public static String joinActionItems(List<String> actionItems) {
        if (actionItems == null) {
            return "";
        }
        return String.join("\n", actionItems);
    }
}

