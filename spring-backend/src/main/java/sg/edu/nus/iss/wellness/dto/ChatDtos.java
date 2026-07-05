package sg.edu.nus.iss.wellness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for RAG chatbot endpoints.
 *
 * @author SA62 Team
 */
public final class ChatDtos {
    private ChatDtos() {
    }

    public record ChatRequest(
            @NotBlank(message = "Question cannot be empty")
            @Size(max = 1000, message = "Question must be less than 1000 characters")
            String question
    ) {
    }

    public record SourceSnippet(String title, String snippet) {
    }

    public record ChatResponse(
            Long id,
            String question,
            String answer,
            List<SourceSnippet> sources,
            String modelName,
            Instant createdAt
    ) {
    }

    public record AiChatRequest(Long userId, String question, List<RecentRecord> recentRecords) {
    }

    public record AiChatResponse(String answer, List<SourceSnippet> sources, String modelName) {
    }

    public record RecentRecord(
            String recordDate,
            double sleepHours,
            String exerciseType,
            int exerciseMinutes,
            int moodScore
    ) {
    }
}

