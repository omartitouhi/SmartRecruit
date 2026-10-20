package com.smartrecruit.application.service;

import com.smartrecruit.application.client.AuditClient;
import com.smartrecruit.application.client.CandidateReadinessClient;
import com.smartrecruit.application.client.CandidateReadinessClient.Readiness;
import com.smartrecruit.application.client.CompanyDirectoryClient;
import com.smartrecruit.application.client.JobDirectoryClient;
import com.smartrecruit.application.client.JobDirectoryClient.JobSummary;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.exception.ApiException;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.repository.ApplicationRepository;
import com.smartrecruit.application.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applications;
    private final JobDirectoryClient jobs;
    private final CandidateReadinessClient readiness;
    private final CompanyDirectoryClient companies;
    private final AuditClient audit;
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

    /** BE-APP-02: newest first, withdrawn ones included. */
    public List<Application> mine(UUID candidateId) {
        return applications.findByCandidateIdOrderByAppliedAtDesc(candidateId);
    }

    /**
     * BE-APP-03: active applications of a job, best score first (unscored last). A recruiter must belong
     * to the company owning the job (403), an admin sees every job.
     */
    public List<Application> receivedFor(AuthenticatedUser user, UUID jobId) {
        JobSummary job = jobs.find(jobId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!"ADMIN".equals(user.role())) {
            requireMemberOf(user.id(), job.companyId());
        }
        return applications.findByJobIdAndStatusNot(jobId, ApplicationStatus.WITHDRAWN).stream()
                .sorted(Comparator.comparing((Application a) -> a.getMatchScore() == null ? null : a.getMatchScore().getScore(),
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Application::getAppliedAt))
                .toList();
    }

    /**
     * BE-APP-04: a recruiter of the owning company moves the application along
     * {@link ApplicationStatus#ALLOWED_TRANSITIONS}; WITHDRAWN is reserved for the candidate.
     */
    public Application changeStatus(UUID recruiterId, UUID applicationId, ApplicationStatus target) {
        Application application = applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        requireMemberOf(recruiterId, application.getCompanyId());
        ApplicationStatus current = application.getStatus();
        if (target == ApplicationStatus.WITHDRAWN || !current.canTransitionTo(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transition interdite : " + current + " -> " + target);
        }
        application.setStatus(target);
        application.getStatusHistory().add(new StatusHistoryEntry(target, clock.instant(), recruiterId));
        Application saved = applications.save(application);
        audit.record(recruiterId, "APPLICATION_STATUS_CHANGED", "APPLICATION", saved.getId());
        return saved;
    }

    /** BE-APP-05: the candidate withdraws their own application, only while RECEIVED or IN_REVIEW (else 409). */
    public void withdraw(UUID candidateId, UUID applicationId) {
        Application application = applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        if (!application.getCandidateId().equals(candidateId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cette candidature ne vous appartient pas");
        }
        if (application.getStatus() != ApplicationStatus.RECEIVED && application.getStatus() != ApplicationStatus.IN_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "Une candidature " + application.getStatus() + " ne peut plus être retirée");
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.getStatusHistory().add(new StatusHistoryEntry(ApplicationStatus.WITHDRAWN, clock.instant(), candidateId));
        applications.save(application);
    }

    private void requireMemberOf(UUID recruiterId, UUID companyId) {
        boolean member = companies.findByMember(recruiterId)
                .map(company -> company.id().equals(companyId)).orElse(false);
        if (!member) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cette offre n'appartient pas à votre entreprise");
        }
    }
}
