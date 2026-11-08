package com.smartrecruit.job.service;

import com.smartrecruit.job.client.CompanyDirectoryClient;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.dto.JobSummaryResponse;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.model.SavedJob;
import com.smartrecruit.job.repository.JobRepository;
import com.smartrecruit.job.repository.SavedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Offers a candidate bookmarks (EXT-SAVED-01). Only published offers can be saved; the list shows the
 * saved offers still published or closed, the others (draft, moderated, deleted) are hidden.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavedJobService {

    /** Bookmarks per candidate. */
    public static final int MAX_SAVED = 200;

    private static final Set<JobStatus> LISTED = Set.of(JobStatus.PUBLISHED, JobStatus.CLOSED);

    private final SavedJobRepository savedJobs;
    private final JobRepository jobRepository;
    private final CompanyDirectoryClient companyClient;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /** Idempotent; 404 for an unknown or unpublished offer, 409 beyond {@link #MAX_SAVED}. */
    public void save(UUID candidateId, UUID jobId) {
        try {
            transaction.executeWithoutResult(status -> {
                Job job = jobRepository.findById(jobId).orElse(null);
                if (job == null || job.getStatus() != JobStatus.PUBLISHED) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable");
                }
                if (savedJobs.existsByCandidateIdAndJobId(candidateId, jobId)) {
                    return;
                }
                if (savedJobs.countByCandidateId(candidateId) >= MAX_SAVED) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "Vous avez atteint la limite de %d offres favorites".formatted(MAX_SAVED));
                }
                savedJobs.save(SavedJob.builder().id(UUID.randomUUID()).candidateId(candidateId).jobId(jobId)
                        .savedAt(clock.instant()).build());
            });
        } catch (DataIntegrityViolationException e) {
            // Saved concurrently by another request: the unique constraint makes the call idempotent.
            log.debug("Offre {} déjà en favori", jobId);
        }
    }

    /** Idempotent, whatever the state of the offer. */
    public void remove(UUID candidateId, UUID jobId) {
        transaction.executeWithoutResult(status -> savedJobs.deleteByCandidateIdAndJobId(candidateId, jobId));
    }

    /** Saved offers still published or closed, most recently saved first. */
    public List<JobSummaryResponse> list(UUID candidateId) {
        List<JobResponse> jobs = transaction.execute(status -> {
            List<SavedJob> saved = savedJobs.findByCandidateIdOrderBySavedAtDesc(candidateId);
            Map<UUID, Job> byId = jobRepository.findAllById(saved.stream().map(SavedJob::getJobId).toList()).stream()
                    .collect(Collectors.toMap(Job::getId, Function.identity()));
            return saved.stream()
                    .map(entry -> byId.get(entry.getJobId()))
                    .filter(Objects::nonNull)
                    .filter(job -> LISTED.contains(job.getStatus()))
                    .map(JobResponse::from)
                    .toList();
        });
        Map<UUID, String> names = companyNames(jobs);
        return jobs.stream().map(job -> new JobSummaryResponse(job, names.get(job.getCompanyId()), null, true)).toList();
    }

    /** @return the ids among {@code jobIds} the candidate saved */
    public Set<UUID> savedAmong(UUID candidateId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Set.of();
        }
        return transaction.execute(status -> new HashSet<>(savedJobs.findSavedJobIds(candidateId, jobIds)));
    }

    /** Right to be forgotten (BE-SEC-02 / EXT-SAVED-01): idempotent. */
    public void eraseCandidate(UUID candidateId) {
        transaction.executeWithoutResult(status -> savedJobs.deleteByCandidateId(candidateId));
    }

    private Map<UUID, String> companyNames(List<JobResponse> jobs) {
        Set<UUID> companyIds = jobs.stream().map(JobResponse::getCompanyId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        try {
            return companyClient.namesOf(companyIds);
        } catch (RestClientException e) {
            log.warn("user-service indisponible, favoris renvoyés sans nom d'entreprise : {}", e.getMessage());
            return Map.of();
        }
    }
}
