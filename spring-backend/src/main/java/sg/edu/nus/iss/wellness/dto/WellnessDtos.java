package sg.edu.nus.iss.wellness.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTOs for wellness record endpoints.
 *
 * @author SA62 Team
 */
public final class WellnessDtos {
    private WellnessDtos() {
    }

    public record WellnessRecordRequest(
            @NotNull LocalDate recordDate,
            @NotNull @DecimalMin("0.0") @DecimalMax("24.0") BigDecimal sleepHours,
            String exerciseType,
            @NotNull @Min(0) Integer exerciseMinutes,
            @NotNull @Min(1) @Max(5) Integer moodScore,
            String notes
    ) {
    }

    public record WellnessRecordResponse(
            Long id,
            LocalDate recordDate,
            BigDecimal sleepHours,
            String exerciseType,
            Integer exerciseMinutes,
            Integer moodScore,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

