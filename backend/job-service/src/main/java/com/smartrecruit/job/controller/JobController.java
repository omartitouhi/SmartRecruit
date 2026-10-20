package com.smartrecruit.job.controller;

import com.smartrecruit.job.dto.JobCreateRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.security.AuthenticatedUser;
import com.smartrecruit.job.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offers API. {@code GET /api/v1/jobs/recommendations} lives in {@link RecommendationController};
 * its literal path wins over {@code /{id}}.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /** Publishes an offer for the recruiter's company — BE-JOB-01. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECRUITER')")
    public JobResponse publish(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody JobCreateRequest request) {
        return jobService.publish(user.id(), request);
    }
}
