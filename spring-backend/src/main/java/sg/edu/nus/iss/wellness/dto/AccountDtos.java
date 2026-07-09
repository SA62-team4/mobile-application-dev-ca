package sg.edu.nus.iss.wellness.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for the privacy / account-management endpoints (S-03).
 *
 * <p>{@link AccountExport} is the full, portable copy of everything the app
 * stores about a user, returned by {@code GET /api/account/export}.
 *
 * @author Chua Wei Yi Justin, Tiong Zhong Cheng
 */
public final class AccountDtos {
    private AccountDtos() {
    }

    /** Account profile fields safe to hand back to the owner (no password hash). */
    public record UserProfile(
            Long id,
            String email,
            String displayName,
            java.math.BigDecimal heightCm,
            String role,
            Instant createdAt
    ) {
    }

    /** Profile update payload for the authenticated owner. */
    public record ProfileUpdateRequest(
            java.math.BigDecimal heightCm
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
     * Optional password re-confirmation body for the permanent delete. Local
     * password accounts must provide the current password; SSO-only accounts
     * have no app password to re-enter, so the authenticated JWT is enough.
     */
    public record DeleteAccountRequest(
            String password
    ) {
    }
}
