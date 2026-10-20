package com.smartrecruit.job.service;

import com.smartrecruit.job.dto.JobCreateRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Recruiter write operations on offers: publish (BE-JOB-01), edit (BE-JOB-02), status (BE-JOB-03).
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final RecruiterCompanies recruiterCompanies;
    private final Clock clock;

    /** Publishes an offer for the recruiter's company straight away. */
    public JobResponse publish(UUID recruiterId, JobCreateRequest request) {
        UUID companyId = recruiterCompanies.requireCompanyId(recruiterId);
        Instant now = clock.instant();
        Job job = jobRepository.save(Job.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .title(request.getTitle())
                .description(request.getDescription())
                .requiredSkills(new ArrayList<>(request.getRequiredSkills()))
                .experienceLevel(request.getExperienceLevel())
                .contractType(request.getContractType())
                .location(request.getLocation())
                .status(JobStatus.PUBLISHED)
                .publishedAt(now)
                .updatedAt(now)
                .build());
        return JobResponse.from(job);
    }
}
