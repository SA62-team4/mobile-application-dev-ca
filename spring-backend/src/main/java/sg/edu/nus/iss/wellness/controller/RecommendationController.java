package sg.edu.nus.iss.wellness.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.client.AiServiceClient;
import sg.edu.nus.iss.wellness.dto.RecommendationDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.repository.RecommendationRepository;
import sg.edu.nus.iss.wellness.service.CurrentUserService;
import sg.edu.nus.iss.wellness.service.DtoMapper;

import java.util.List;

/**
 * Provides recommendation generation and listing APIs.
 *
 * @author Tiong Zhong Cheng
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final CurrentUserService currentUserService;
    private final RecommendationRepository recommendations;
    private final AiServiceClient aiServiceClient;

    public RecommendationController(CurrentUserService currentUserService,
                                    RecommendationRepository recommendations,
                                    AiServiceClient aiServiceClient) {
        this.currentUserService = currentUserService;
        this.recommendations = recommendations;
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public RecommendationDtos.RecommendationResponse generate() {
        AppUser user = currentUserService.requireCurrentUser();
        return aiServiceClient.generateRecommendation(user.getId());
    }

    @GetMapping
    public List<RecommendationDtos.RecommendationResponse> list() {
        AppUser user = currentUserService.requireCurrentUser();
        return recommendations.findByUserOrderByCreatedAtDesc(user).stream()
                .map(DtoMapper::recommendation)
                .toList();
    }
}

