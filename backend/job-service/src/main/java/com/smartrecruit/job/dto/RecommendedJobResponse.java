package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.Job;

import java.time.Instant;
import java.util.UUID;

/**
 * One recommended offer, ready to display (BE-REC-01): offer details joined with the AI score.
 * Shape documented in {@code DATA_MODEL.md} §4.7. {@code companyName} is null when user-service
 * could not be reached.
 */
public record RecommendedJobResponse(
        UUID jobId,
        String title,
        UUID companyId,
        String companyName,
        String location,
        ContractType contractType,
        ExperienceLevel experienceLevel,
        Instant publishedAt,
        double score
) {

    public static RecommendedJobResponse of(Job job, String companyName, double score) {
        return new RecommendedJobResponse(job.getId(), job.getTitle(), job.getCompanyId(), companyName,
                job.getLocation(), job.getContractType(), job.getExperienceLevel(), job.getPublishedAt(), score);
    }
}
