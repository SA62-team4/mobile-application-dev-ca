package sg.edu.nus.iss.wellness.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTOs for wellness record endpoints.
 *
 * @author Tang Chee Seng, Tiong Zhong Cheng
 */
        public final class WellnessDtos {
        private WellnessDtos() {
        }

        public record WellnessRecordRequest(
            @NotNull(message = "is required")
            LocalDate recordDate,

            @NotNull(message = "is required")
            @DecimalMin(value = "0.0", message = "must be between 0 and 24")
            @DecimalMax(value = "24.0", message = "must be between 0 and 24")
            BigDecimal sleepHours,

            @DecimalMin(value = "0.0", inclusive = false, message = "must be positive")
            BigDecimal weightKg,

            String exerciseType,

            @NotNull(message = "is required")
            @Min(value = 0, message = "must be 0 or greater")
            Integer exerciseMinutes,

            @NotNull(message = "is required")
            @Min(value = 1, message = "must be between 1 and 5")
            @Max(value = 5, message = "must be between 1 and 5")
            Integer moodScore,

            String notes
    ) {
    }

    public record WellnessRecordResponse(
            Long id,
            LocalDate recordDate,
            BigDecimal sleepHours,
            BigDecimal weightKg,
            String exerciseType,
            Integer exerciseMinutes,
            Integer moodScore,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

