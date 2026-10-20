package com.smartrecruit.job.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.smartrecruit.job.model.Job;

/**
 * {@link JobResponse} plus what lists and detail pages display: {@code companyName} (null when
 * user-service is unreachable) and {@code applicationsCount} (only on a recruiter's own listing,
 * null elsewhere).
 */
public record JobSummaryResponse(@JsonUnwrapped JobResponse job, String companyName, Long applicationsCount) {

    public static JobSummaryResponse of(Job job, String companyName, Long applicationsCount) {
        return new JobSummaryResponse(JobResponse.from(job), companyName, applicationsCount);
    }
}
