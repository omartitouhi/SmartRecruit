package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResponse {

    private UUID id;
    private UUID companyId;
    private String title;
    private String description;
    private List<String> requiredSkills;
    private ExperienceLevel experienceLevel;
    private ContractType contractType;
    private String location;
    private JobStatus status;
    private Instant publishedAt;
    private Instant updatedAt;
    /** Only shown to the owning company and admins; null for everybody else. */
    private String moderationReason;

    /** Public view of the offer: without the moderation reason. */
    public static JobResponse from(Job job) {
        return from(job, false);
    }

    /** @param withModerationReason true for the owning company and admins */
    public static JobResponse from(Job job, boolean withModerationReason) {
        return JobResponse.builder()
                .id(job.getId())
                .companyId(job.getCompanyId())
                .title(job.getTitle())
                .description(job.getDescription())
                .requiredSkills(job.getRequiredSkills())
                .experienceLevel(job.getExperienceLevel())
                .contractType(job.getContractType())
                .location(job.getLocation())
                .status(job.getStatus())
                .publishedAt(job.getPublishedAt())
                .updatedAt(job.getUpdatedAt())
                .moderationReason(withModerationReason ? job.getModerationReason() : null)
                .build();
    }
}
