package com.smartrecruit.job.controller;

import com.smartrecruit.job.dto.JobCreateRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.dto.JobStatusRequest;
import com.smartrecruit.job.dto.JobSummaryResponse;
import com.smartrecruit.job.dto.PageResponse;
import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.security.AuthenticatedUser;
import com.smartrecruit.job.service.JobPatchParser;
import com.smartrecruit.job.service.JobQueryService;
import com.smartrecruit.job.service.JobSearchCriteria;
import com.smartrecruit.job.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Offers API. {@code GET /api/v1/jobs/recommendations} lives in {@link RecommendationController};
 * its literal path wins over {@code /{id}}.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobPatchParser patchParser;
    private final JobQueryService queryService;

    /**
     * Search and filters, newest publication first — BE-JOB-04. {@code skills} is a comma-separated
     * list (the offer needs at least one of them).
     */
    @GetMapping
    public PageResponse<JobSummaryResponse> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 100) String location,
            @RequestParam(required = false) List<String> skills,
            @RequestParam(required = false) ContractType contractType,
            @RequestParam(required = false) ExperienceLevel experienceLevel,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return queryService.search(user,
                new JobSearchCriteria(keyword, location, skills, contractType, experienceLevel, companyId, status),
                page, size);
    }

    /** Publishes an offer for the recruiter's company — BE-JOB-01. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECRUITER')")
    public JobResponse publish(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody JobCreateRequest request) {
        return jobService.publish(user.id(), request);
    }

    /** Partial update (JSON Merge Patch) of an offer of the recruiter's company — BE-JOB-02. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                              @RequestBody JsonNode body) {
        return jobService.update(user.id(), id, patchParser.parse(body));
    }

    /** Publishes, closes or reopens an offer — BE-JOB-03. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                    @Valid @RequestBody JobStatusRequest request) {
        return jobService.changeStatus(user.id(), id, request.status());
    }
}
