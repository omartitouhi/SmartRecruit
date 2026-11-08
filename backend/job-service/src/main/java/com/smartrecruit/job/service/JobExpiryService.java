package com.smartrecruit.job.service;

import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Automatic closing of offers whose application deadline is over (EXT-JOB-01). The deadline is the
 * last day (UTC) on which one can apply, so an offer is closed from the next day on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExpiryService {

    private final JobRepository jobRepository;
    private final Clock clock;

    /** @return number of offers closed by this run */
    @Transactional
    public int closeExpiredOffers() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<Job> expired = jobRepository.findByStatusAndApplicationDeadlineBefore(JobStatus.PUBLISHED, today);
        for (Job job : expired) {
            job.setStatus(JobStatus.CLOSED);
            job.setUpdatedAt(now);
        }
        jobRepository.saveAll(expired);
        if (!expired.isEmpty()) {
            log.info("{} offre(s) clôturée(s) automatiquement : date limite de candidature dépassée", expired.size());
        }
        return expired.size();
    }
}
