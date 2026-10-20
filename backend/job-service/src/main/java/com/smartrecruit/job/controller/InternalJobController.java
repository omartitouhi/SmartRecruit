package com.smartrecruit.job.controller;

import com.smartrecruit.job.dto.InternalJobResponse;
import com.smartrecruit.job.dto.JobModerationRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.repository.JobRepository;
import com.smartrecruit.job.service.JobModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Backend-to-backend endpoints. {@code /internal/**} is not routed by the api-gateway and requires
 * a signed {@code SERVICE} identity.
 */
@RestController
@RequestMapping("/internal/v1/jobs")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalJobController {

    private final JobRepository jobRepository;
    private final JobModerationService moderationService;

    /** Identity and state of an offer, whatever its status. */
    @GetMapping("/{id}")
    public InternalJobResponse get(@PathVariable UUID id) {
        return jobRepository.findById(id).map(InternalJobResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
    }

    /** Withdraws an offer on behalf of an admin (dashboard-service) — BE-ADMIN-02. */
    @PatchMapping("/{id}/moderation")
    public JobResponse moderate(@PathVariable UUID id, @Valid @RequestBody JobModerationRequest request) {
        return moderationService.moderate(id, request.reason());
    }
}
