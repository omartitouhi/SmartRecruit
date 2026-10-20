package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal view of an offer for other services (application-service checks the company and status,
 * and keys its match cache on {@code updatedAt}).
 */
public record InternalJobResponse(UUID id, UUID companyId, String title, JobStatus status, Instant updatedAt) {

    public static InternalJobResponse from(Job job) {
        return new InternalJobResponse(job.getId(), job.getCompanyId(), job.getTitle(), job.getStatus(), job.getUpdatedAt());
    }
}
