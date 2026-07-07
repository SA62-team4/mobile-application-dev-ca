package sg.edu.nus.iss.wellness.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for recommendation endpoints.
 *
 * @author Tiong Zhong Cheng
 */
public final class RecommendationDtos {
    private RecommendationDtos() {
    }

    public record RecommendationResponse(
            Long id,
            String title,
            String trendSummary,
            String recommendationText,
            List<String> actionItems,
            String generatedBy,
            Instant createdAt
    ) {
    }

    public record InternalRecommendationRequest(
            String title,
            String trendSummary,
            String recommendationText,
            List<String> actionItems,
            String generatedBy
    ) {
    }
}

