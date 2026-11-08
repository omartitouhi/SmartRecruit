package com.smartrecruit.job.controller;

import com.smartrecruit.job.dto.RecommendedJobResponse;
import com.smartrecruit.job.security.AuthenticatedUser;
import com.smartrecruit.job.service.RecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** Personalised offers of the connected candidate, best match first — BE-REC-01. */
    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<RecommendedJobResponse> myRecommendations(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return recommendationService.recommendFor(user.id(), limit);
    }
}
