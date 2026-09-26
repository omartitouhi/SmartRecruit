package com.smartrecruit.job.dto;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code POST /api/v1/jobs}. {@code status} defaults to {@code DRAFT}
 * server-side and is never accepted from the client on creation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCreateRequest {

    private UUID companyId;
    private String title;
    private String description;
    private List<String> requiredSkills;
    private ExperienceLevel experienceLevel;
    private ContractType contractType;
    private String location;
}
