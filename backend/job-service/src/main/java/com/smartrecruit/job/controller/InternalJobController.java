package com.smartrecruit.job.controller;

import com.smartrecruit.job.dto.InternalJobResponse;
import com.smartrecruit.job.dto.JobModerationRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.dto.JobStats;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import com.smartrecruit.job.repository.JobRepository.StatusCount;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final Clock clock;

    /** Identity and state of an offer, whatever its status. */
    @GetMapping("/{id}")
    public InternalJobResponse get(@PathVariable UUID id) {
        return jobRepository.findById(id).map(InternalJobResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
    }

    /** Offers per status, and published offers closing within 7 days (EXT-JOB-01), for one company or the platform. */
    @GetMapping("/stats")
    public JobStats stats(@RequestParam(required = false) UUID companyId) {
        List<StatusCount> counts = companyId == null
                ? jobRepository.countByStatus() : jobRepository.countByStatusForCompany(companyId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate inAWeek = today.plusDays(7);
        long closingSoon = companyId == null
                ? jobRepository.countClosingBetween(JobStatus.PUBLISHED, today, inAWeek)
                : jobRepository.countClosingBetweenForCompany(companyId, JobStatus.PUBLISHED, today, inAWeek);
        return JobStats.of(counts.stream().collect(Collectors.toMap(StatusCount::getStatus, StatusCount::getTotal)),
                closingSoon);
    }

    /** Withdraws an offer on behalf of an admin (dashboard-service) — BE-ADMIN-02. */
    @PatchMapping("/{id}/moderation")
    public JobResponse moderate(@PathVariable UUID id, @Valid @RequestBody JobModerationRequest request) {
        return moderationService.moderate(id, request.reason());
    }
}
