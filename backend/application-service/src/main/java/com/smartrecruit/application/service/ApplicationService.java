package com.smartrecruit.application.service;

import com.smartrecruit.application.client.CandidateReadinessClient;
import com.smartrecruit.application.client.CandidateReadinessClient.Readiness;
import com.smartrecruit.application.client.JobDirectoryClient;
import com.smartrecruit.application.client.JobDirectoryClient.JobSummary;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.exception.ApiException;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applications;
    private final JobDirectoryClient jobs;
    private final CandidateReadinessClient readiness;
    private final ApplicationScoring scoring;
    private final MatchScoreService matchScores;
    private final Clock clock;

    /** BE-APP-01: checks, in the contract order: job exists (404), is published (409), candidate ready (422), no duplicate (409). The score is computed afterwards in the background (BE-MATCH-01). */
    public Application apply(UUID candidateId, UUID jobId) {
        JobSummary job = jobs.find(jobId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!"PUBLISHED".equals(job.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "L'offre n'accepte pas de candidatures");
        }
        Readiness candidate = readiness.find(candidateId).orElse(null);
        if (candidate == null || !candidate.profileComplete() || !candidate.hasCv()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Profil complet et CV requis pour postuler");
        }
        if (applications.existsByJobIdAndCandidateIdAndStatusNot(jobId, candidateId, ApplicationStatus.WITHDRAWN)) {
            throw new ApiException(HttpStatus.CONFLICT, "Vous avez déjà postulé à cette offre");
        }
        Instant now = clock.instant();
        Application application = Application.builder()
                .id(UUID.randomUUID()).jobId(jobId).candidateId(candidateId).companyId(job.companyId())
                .status(ApplicationStatus.RECEIVED).appliedAt(now).build();
        application.getStatusHistory().add(new StatusHistoryEntry(ApplicationStatus.RECEIVED, now, candidateId));
        Application saved = applications.save(application);
        // Without an analysed CV there is nothing to score; the application stays valid without a score.
        if (candidate.cvParsedAt() != null) {
            scoring.scoreInBackground(saved.getId(), candidateId, jobId, candidate.cvParsedAt(), job.updatedAt());
        }
        return saved;
    }

    /** BE-MATCH-02: score of the candidate for a job, cached; 404 unknown job, 422 no analysed CV, 503 if the AI fails. */
    public MatchScoreDto matchPreview(UUID candidateId, UUID jobId) {
        JobSummary job = jobs.find(jobId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        Readiness candidate = readiness.find(candidateId).orElse(null);
        if (candidate == null || candidate.cvParsedAt() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Un CV analysé est requis pour calculer la compatibilité");
        }
        return matchScores.scoreFor(candidateId, jobId, candidate.cvParsedAt(), job.updatedAt());
    }
}
