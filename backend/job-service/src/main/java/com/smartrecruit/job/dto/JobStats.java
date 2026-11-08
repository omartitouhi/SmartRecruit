package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.JobStatus;

import java.util.Map;

/**
 * Offers per status, for the dashboards ({@code GET /internal/v1/jobs/stats}). {@code closingSoon}
 * (EXT-JOB-01): published offers whose deadline falls within the next 7 days, today included.
 */
public record JobStats(long total, long draft, long published, long closed, long moderated, long closingSoon) {

    public static JobStats of(Map<JobStatus, Long> countByStatus, long closingSoon) {
        long draft = countByStatus.getOrDefault(JobStatus.DRAFT, 0L);
        long published = countByStatus.getOrDefault(JobStatus.PUBLISHED, 0L);
        long closed = countByStatus.getOrDefault(JobStatus.CLOSED, 0L);
        long moderated = countByStatus.getOrDefault(JobStatus.MODERATED, 0L);
        return new JobStats(draft + published + closed + moderated, draft, published, closed, moderated, closingSoon);
    }
}
