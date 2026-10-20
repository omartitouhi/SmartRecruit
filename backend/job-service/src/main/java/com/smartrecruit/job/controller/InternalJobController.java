package com.smartrecruit.job.controller;

import com.smartrecruit.job.dto.JobModerationRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.service.JobModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final JobModerationService moderationService;

    /** Withdraws an offer on behalf of an admin (dashboard-service) — BE-ADMIN-02. */
    @PatchMapping("/{id}/moderation")
    public JobResponse moderate(@PathVariable UUID id, @Valid @RequestBody JobModerationRequest request) {
        return moderationService.moderate(id, request.reason());
    }
}
