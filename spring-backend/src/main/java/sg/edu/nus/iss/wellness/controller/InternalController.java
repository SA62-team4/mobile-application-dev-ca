package sg.edu.nus.iss.wellness.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.config.AppProperties;
import sg.edu.nus.iss.wellness.dto.RecommendationDtos;
import sg.edu.nus.iss.wellness.dto.WellnessDtos;
import sg.edu.nus.iss.wellness.error.ApiException;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.Recommendation;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.RecommendationRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.service.DtoMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Internal APIs used by the Python agentic AI service.
 *
 * @author SA62 Team
 */
@RestController
@RequestMapping("/api/internal/users/{userId}")
public class InternalController {
    private final AppProperties properties;
    private final AppUserRepository users;
    private final WellnessRecordRepository wellnessRecords;
    private final RecommendationRepository recommendations;

    public InternalController(AppProperties properties,
                              AppUserRepository users,
                              WellnessRecordRepository wellnessRecords,
                              RecommendationRepository recommendations) {
        this.properties = properties;
        this.users = users;
        this.wellnessRecords = wellnessRecords;
        this.recommendations = recommendations;
    }

    @GetMapping("/wellness-records")
    public List<WellnessDtos.WellnessRecordResponse> records(@RequestHeader("X-Internal-Service-Token") String token,
                                                             @PathVariable Long userId,
                                                             @RequestParam(defaultValue = "14") int days) {
        requireInternalToken(token);
        AppUser user = requireUser(userId);
        LocalDate from = LocalDate.now(ZoneId.systemDefault()).minusDays(days);
        return wellnessRecords.findByUserAndRecordDateAfterOrderByRecordDateDesc(user, from).stream()
                .map(DtoMapper::wellness)
                .toList();
    }

    @PostMapping("/recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    public RecommendationDtos.RecommendationResponse saveRecommendation(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable Long userId,
            @Valid @RequestBody RecommendationDtos.InternalRecommendationRequest request) {
        requireInternalToken(token);
        AppUser user = requireUser(userId);
        Recommendation recommendation = new Recommendation();
        recommendation.setUser(user);
        recommendation.setTitle(request.title());
        recommendation.setTrendSummary(request.trendSummary());
        recommendation.setRecommendationText(request.recommendationText());
        recommendation.setActionItems(DtoMapper.joinActionItems(request.actionItems()));
        recommendation.setGeneratedBy(request.generatedBy() == null ? "python-agent" : request.generatedBy());
        return DtoMapper.recommendation(recommendations.save(recommendation));
    }

    private AppUser requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireInternalToken(String token) {
        if (!properties.getInternalServiceToken().equals(token)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Invalid internal service token");
        }
    }
}
