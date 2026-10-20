package com.smartrecruit.job.service;

import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Withdrawal of an offer by an admin, requested by dashboard-service (BE-ADMIN-02). A moderated offer
 * leaves candidate search and can no longer be edited or reopened by its company.
 */
@Service
@RequiredArgsConstructor
public class JobModerationService {

    private final JobRepository jobRepository;
    private final Clock clock;

    /** Moderating again an already moderated offer replaces the reason. */
    @Transactional
    public JobResponse moderate(UUID jobId, String reason) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        Instant now = clock.instant();
        job.setStatus(JobStatus.MODERATED);
        job.setModerationReason(reason);
        job.setModeratedAt(now);
        job.setUpdatedAt(now);
        return JobResponse.from(jobRepository.save(job), true);
    }
}
