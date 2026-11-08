package com.smartrecruit.application.service;

import com.smartrecruit.application.client.AuditClient;
import com.smartrecruit.application.client.CandidateReadinessClient;
import com.smartrecruit.application.client.CandidateReadinessClient.Readiness;
import com.smartrecruit.application.client.CompanyDirectoryClient;
import com.smartrecruit.application.client.CompanyDirectoryClient.CompanySummary;
import com.smartrecruit.application.client.JobDirectoryClient;
import com.smartrecruit.application.client.JobDirectoryClient.JobSummary;
import com.smartrecruit.application.dto.InterviewRequest;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.exception.ApiException;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationStatus;
import com.smartrecruit.application.model.Interview;
import com.smartrecruit.application.model.StatusHistoryEntry;
import com.smartrecruit.application.repository.ApplicationRepository;
import com.smartrecruit.application.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** At most this many upcoming interviews are listed (EXT-APP-02). */
    static final int UPCOMING_INTERVIEWS_LIMIT = 50;

    private static final int DEFAULT_INTERVIEW_MINUTES = 60;

    private final ApplicationRepository applications;
    private final JobDirectoryClient jobs;
    private final CandidateReadinessClient readiness;
    private final CompanyDirectoryClient companies;
    private final AuditClient audit;
    private final ApplicationScoring scoring;
    private final MatchScoreService matchScores;
    private final Clock clock;

    /**
     * BE-APP-01: checks, in the contract order: job exists (404), is published (409), deadline not passed (409,
     * EXT-JOB-01), candidate ready (422), no duplicate (409). The optional cover letter is kept (EXT-APP-01). The
     * score is computed afterwards in the background (BE-MATCH-01).
     */
    public Application apply(UUID candidateId, UUID jobId, String coverLetter) {
        JobSummary job = jobs.find(jobId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!"PUBLISHED".equals(job.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "L'offre n'accepte pas de candidatures");
        }
        if (job.applicationDeadline() != null && LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).isAfter(job.applicationDeadline())) {
            throw new ApiException(HttpStatus.CONFLICT, "La date limite de candidature est dépassée");
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
                .status(ApplicationStatus.RECEIVED).appliedAt(now).coverLetter(blankToNull(coverLetter)).build();
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
     * {@link ApplicationStatus#ALLOWED_TRANSITIONS}; WITHDRAWN is reserved for the candidate. The optional
     * {@code message} is recorded in the new history entry for the candidate (EXT-APP-01); an {@code interview}
     * may be planned in the same call, only when moving to INTERVIEW (EXT-APP-02).
     */
    public Application changeStatus(UUID recruiterId, UUID applicationId, ApplicationStatus target, String message,
                                     InterviewRequest interview) {
        Application application = findApplication(applicationId);
        requireMemberOf(recruiterId, application.getCompanyId());
        ApplicationStatus current = application.getStatus();
        if (target == ApplicationStatus.WITHDRAWN || !current.canTransitionTo(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transition interdite : " + current + " -> " + target);
        }
        if (interview != null && target != ApplicationStatus.INTERVIEW) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Un entretien ne peut être planifié qu'avec le statut INTERVIEW");
        }
        Interview planned = interview == null ? null : toInterview(interview);
        application.setStatus(target);
        application.getStatusHistory().add(new StatusHistoryEntry(target, clock.instant(), recruiterId, blankToNull(message)));
        if (planned != null) {
            application.setInterview(planned);
        }
        Application saved = applications.save(application);
        audit.record(recruiterId, "APPLICATION_STATUS_CHANGED", "APPLICATION", saved.getId());
        if (planned != null) {
            audit.record(recruiterId, "INTERVIEW_SCHEDULED", "APPLICATION", saved.getId());
        }
        return saved;
    }

    /** EXT-APP-02: plans or reschedules the interview of an application in INTERVIEW (else 409). */
    public Application scheduleInterview(UUID recruiterId, UUID applicationId, InterviewRequest request) {
        Application application = findApplication(applicationId);
        requireMemberOf(recruiterId, application.getCompanyId());
        if (application.getStatus() != ApplicationStatus.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "La candidature doit être au statut INTERVIEW pour planifier un entretien");
        }
        application.setInterview(toInterview(request));
        Application saved = applications.save(application);
        audit.record(recruiterId, "INTERVIEW_SCHEDULED", "APPLICATION", saved.getId());
        return saved;
    }

    /** EXT-APP-02: upcoming interviews of the recruiter's company, soonest first; none without a company. */
    public List<Application> upcomingInterviews(UUID recruiterId) {
        Optional<CompanySummary> company = companies.findByMember(recruiterId);
        if (company.isEmpty()) {
            return List.of();
        }
        return applications.upcomingInterviews(company.get().id(), ApplicationStatus.INTERVIEW, clock.instant(),
                Limit.of(UPCOMING_INTERVIEWS_LIMIT));
    }

    /** BE-APP-05: the candidate withdraws their own application, only while RECEIVED or IN_REVIEW (else 409). */
    public void withdraw(UUID candidateId, UUID applicationId) {
        Application application = findApplication(applicationId);
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

    Application findApplication(UUID applicationId) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
    }

    void requireMemberOf(UUID recruiterId, UUID companyId) {
        boolean member = companies.findByMember(recruiterId)
                .map(company -> company.id().equals(companyId)).orElse(false);
        if (!member) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cette offre n'appartient pas à votre entreprise");
        }
    }

    private Interview toInterview(InterviewRequest request) {
        if (!request.scheduledAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "L'entretien doit être planifié dans le futur");
        }
        return Interview.builder()
                .scheduledAt(request.scheduledAt())
                .durationMinutes(request.durationMinutes() == null ? DEFAULT_INTERVIEW_MINUTES : request.durationMinutes())
                .mode(request.mode())
                .location(blankToNull(request.location()))
                .build();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
