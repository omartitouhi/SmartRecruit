package com.smartrecruit.job.service;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.JobStatus;

import java.util.List;

/**
 * Query parameters of {@code GET /api/v1/jobs}; every field is optional. {@code companyId} is a
 * company UUID or {@code me}, {@code status} is only honoured for admins and a recruiter's own offers.
 */
public record JobSearchCriteria(
        String keyword,
        String location,
        List<String> skills,
        ContractType contractType,
        ExperienceLevel experienceLevel,
        String companyId,
        JobStatus status
) {
}
