package com.smartrecruit.job.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * {@link JobResponse} plus what lists and detail pages display: {@code companyName} (null when
 * user-service is unreachable), {@code applicationsCount} (only on a recruiter's own listing,
 * null elsewhere) and {@code saved} (EXT-SAVED-01: whether the candidate bookmarked the offer,
 * null for any other role).
 */
public record JobSummaryResponse(@JsonUnwrapped JobResponse job, String companyName, Long applicationsCount,
                                 Boolean saved) {

    public JobSummaryResponse(JobResponse job, String companyName, Long applicationsCount) {
        this(job, companyName, applicationsCount, null);
    }
}
