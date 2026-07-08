package sg.edu.nus.iss.wellness.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * DTOs for the privacy / account-management endpoints (S-03).
 *
 * <p>{@link AccountExport} is the full, portable copy of everything the app
 * stores about a user, returned by {@code GET /api/account/export}.
 *
 * @author Chua Wei Yi Justin
 */
public final class AccountDtos {
    private AccountDtos() {
    }

    /** Account profile fields safe to hand back to the owner (no password hash). */
    public record UserProfile(
            Long id,
            String email,
            String displayName,
            String role,
            Instant createdAt
    ) {
    }

    /** A stored chatbot exchange, flattened for export. */
    public record ChatExport(
            Long id,
            String question,
            String answer,
            String sourceSummary,
            String modelName,
            Instant createdAt
    ) {
    }

    /** Everything stored about a user, aggregated for a self-service data export. */
    public record AccountExport(
            UserProfile profile,
            List<WellnessDtos.WellnessRecordResponse> wellnessRecords,
            List<RecommendationDtos.RecommendationResponse> recommendations,
            List<ChatExport> chatMessages,
            Instant exportedAt
    ) {
    }

    /**
     * Password re-confirmation body for the permanent delete. Requiring the
     * current password guards against a stolen/forgotten unlocked session
     * triggering an irreversible wipe.
     */
    public record DeleteAccountRequest(
            @NotBlank(message = "Password confirmation is required")
            String password
    ) {
    }
}
