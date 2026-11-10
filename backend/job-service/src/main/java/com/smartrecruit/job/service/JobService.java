package com.smartrecruit.job.service;

import com.smartrecruit.job.dto.JobCreateRequest;
import com.smartrecruit.job.dto.JobPatchRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.exception.RequestValidationException;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.model.WorkMode;
import com.smartrecruit.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Recruiter write operations on offers: publish (BE-JOB-01), edit (BE-JOB-02), status (BE-JOB-03),
 * duplicate (EXT-JOB-01). Salary range, deadline, work mode and positions follow EXT-JOB-01.
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final RecruiterCompanies recruiterCompanies;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;

    private static final String COPY_SUFFIX = " (copie)";
    private static final int TITLE_MAX = 255;

    /** Publishes an offer for the recruiter's company straight away. */
    public JobResponse publish(UUID recruiterId, JobCreateRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        checkDeadline(request.getApplicationDeadline(), errors);
        checkSalaryRange(request.getSalaryMin(), request.getSalaryMax(), errors);
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
        UUID companyId = recruiterCompanies.requireCompanyId(recruiterId);
        Instant now = clock.instant();
        Job job = Job.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .title(request.getTitle())
                .description(request.getDescription())
                .requiredSkills(new ArrayList<>(request.getRequiredSkills()))
                .experienceLevel(request.getExperienceLevel())
                .contractType(request.getContractType())
                .location(request.getLocation())
                .workMode(request.getWorkMode() == null ? WorkMode.ON_SITE : request.getWorkMode())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .applicationDeadline(request.getApplicationDeadline())
                .positions(request.getPositions() == null ? 1 : request.getPositions())
                .status(JobStatus.PUBLISHED)
                .publishedAt(now)
                .updatedAt(now)
                .build();
        // The company lookup above is a remote call: keep it outside the transaction.
        return transaction.execute(status -> {
            jobRepository.save(job);
            events.publishEvent(new JobAnalysisRequested(job.getId(), job.getTitle(), job.getDescription()));
            return JobResponse.from(job);
        });
    }

    /**
     * Merge patch of an offer of the recruiter's company. Closed and moderated offers are frozen;
     * a change of title, description or skills asks for a new AI analysis.
     */
    public JobResponse update(UUID recruiterId, UUID jobId, JobPatchRequest patch) {
        UUID companyId = recruiterCompanies.requireCompanyId(recruiterId);
        return transaction.execute(status -> {
            Job job = ownedJob(jobId, companyId);
            if (job.getStatus() == JobStatus.CLOSED || job.getStatus() == JobStatus.MODERATED) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Une offre clôturée ou retirée par modération ne peut plus être modifiée");
            }
            checkPatchedRules(job, patch);
            boolean analysedContentChanged = applyAnalysedContent(job, patch);
            boolean detailsChanged = applyDetails(job, patch);
            if (analysedContentChanged || detailsChanged) {
                job.setUpdatedAt(clock.instant());
                jobRepository.save(job);
            }
            if (analysedContentChanged) {
                events.publishEvent(new JobAnalysisRequested(job.getId(), job.getTitle(), job.getDescription()));
            }
            return JobResponse.from(job);
        });
    }

    /**
     * Publishes, closes or reopens an offer of the recruiter's company. A moderated offer is out of
     * the recruiter's hands (403); other transitions are limited by {@link JobStatus#canBecome}.
     */
    public JobResponse changeStatus(UUID recruiterId, UUID jobId, JobStatus next) {
        UUID companyId = recruiterCompanies.requireCompanyId(recruiterId);
        return transaction.execute(status -> {
            Job job = ownedJob(jobId, companyId);
            if (job.getStatus() == JobStatus.MODERATED) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Cette offre a été retirée par modération");
            }
            if (!job.getStatus().canBecome(next)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Transition de statut interdite : %s vers %s".formatted(job.getStatus(), next));
            }
            if (next == JobStatus.PUBLISHED && job.isPastDeadline(today())) {
                throw new ApiException(HttpStatus.CONFLICT, job.getStatus() == JobStatus.CLOSED
                        ? "La date limite de candidature est dépassée : dupliquez l'offre pour la republier"
                        : "La date limite de candidature est dépassée : modifiez-la ou supprimez-la avant de publier");
            }
            Instant now = clock.instant();
            job.setStatus(next);
            if (next == JobStatus.PUBLISHED && job.getPublishedAt() == null) {
                job.setPublishedAt(now);
            }
            job.setUpdatedAt(now);
            return JobResponse.from(jobRepository.save(job));
        });
    }

    /**
     * Deletes a draft offer of the recruiter's company. A draft was never published, so no application
     * or bookmark refers to it; a published or closed offer is closed instead (409).
     */
    public void delete(UUID recruiterId, UUID jobId) {
        UUID companyId = recruiterCompanies.requireCompanyId(recruiterId);
        transaction.executeWithoutResult(status -> {
            Job job = ownedJob(jobId, companyId);
            if (job.getStatus() != JobStatus.DRAFT) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Seule une offre en brouillon peut être supprimée : clôturez plutôt une offre publiée");
            }
            jobRepository.delete(job);
        });
    }

    /**
     * Copies an offer of the recruiter's company as a new draft (EXT-JOB-01): title suffixed with
     * "(copie)", no deadline, no publication date. A moderated offer cannot be copied (409).
     */
    public JobResponse duplicate(UUID recruiterId, UUID jobId) {
        UUID companyId = recruiterCompanies.requireCompanyId(recruiterId);
        return transaction.execute(status -> {
            Job source = ownedJob(jobId, companyId);
            if (source.getStatus() == JobStatus.MODERATED) {
                throw new ApiException(HttpStatus.CONFLICT, "Une offre retirée par modération ne peut pas être dupliquée");
            }
            Instant now = clock.instant();
            Job copy = Job.builder()
                    .id(UUID.randomUUID())
                    .companyId(companyId)
                    .title(copyTitle(source.getTitle()))
                    .description(source.getDescription())
                    .requiredSkills(new ArrayList<>(source.getRequiredSkills()))
                    .experienceLevel(source.getExperienceLevel())
                    .contractType(source.getContractType())
                    .location(source.getLocation())
                    .workMode(source.getWorkMode() == null ? WorkMode.ON_SITE : source.getWorkMode())
                    .salaryMin(source.getSalaryMin())
                    .salaryMax(source.getSalaryMax())
                    .positions(source.getPositions() == null ? 1 : source.getPositions())
                    .status(JobStatus.DRAFT)
                    .updatedAt(now)
                    .build();
            jobRepository.save(copy);
            events.publishEvent(new JobAnalysisRequested(copy.getId(), copy.getTitle(), copy.getDescription()));
            return JobResponse.from(copy, true);
        });
    }

    private static String copyTitle(String title) {
        String base = title.length() + COPY_SUFFIX.length() > TITLE_MAX
                ? title.substring(0, TITLE_MAX - COPY_SUFFIX.length()).stripTrailing()
                : title;
        return base + COPY_SUFFIX;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void checkDeadline(LocalDate deadline, Map<String, String> errors) {
        if (deadline != null && deadline.isBefore(today())) {
            errors.put("applicationDeadline", "La date limite doit être aujourd'hui ou plus tard");
        }
    }

    private static void checkSalaryRange(Integer salaryMin, Integer salaryMax, Map<String, String> errors) {
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            errors.put("salaryMax", "Le salaire maximum doit être supérieur ou égal au salaire minimum");
        }
    }

    /** The deadline sent must not be past; the salary range is checked on the values after the patch. */
    private void checkPatchedRules(Job job, JobPatchRequest patch) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (patch.has("applicationDeadline")) {
            checkDeadline(patch.applicationDeadline(), errors);
        }
        Integer salaryMin = patch.has("salaryMin") ? patch.salaryMin() : job.getSalaryMin();
        Integer salaryMax = patch.has("salaryMax") ? patch.salaryMax() : job.getSalaryMax();
        checkSalaryRange(salaryMin, salaryMax, errors);
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
    }

    private Job ownedJob(UUID jobId, UUID companyId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!companyId.equals(job.getCompanyId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cette offre appartient à une autre entreprise");
        }
        return job;
    }

    /** @return true if title, description or skills changed, i.e. what the AI analyzes */
    private boolean applyAnalysedContent(Job job, JobPatchRequest patch) {
        boolean changed = false;
        if (patch.has("title") && !patch.title().equals(job.getTitle())) {
            job.setTitle(patch.title());
            changed = true;
        }
        if (patch.has("description") && !patch.description().equals(job.getDescription())) {
            job.setDescription(patch.description());
            changed = true;
        }
        // Compared as plain lists: Hibernate's collection wrapper has identity equality.
        if (patch.has("requiredSkills") && !patch.requiredSkills().equals(new ArrayList<>(job.getRequiredSkills()))) {
            job.setRequiredSkills(new ArrayList<>(patch.requiredSkills()));
            changed = true;
        }
        return changed;
    }

    private boolean applyDetails(Job job, JobPatchRequest patch) {
        boolean changed = false;
        if (patch.has("experienceLevel") && !Objects.equals(patch.experienceLevel(), job.getExperienceLevel())) {
            job.setExperienceLevel(patch.experienceLevel());
            changed = true;
        }
        if (patch.has("contractType") && !Objects.equals(patch.contractType(), job.getContractType())) {
            job.setContractType(patch.contractType());
            changed = true;
        }
        if (patch.has("location") && !patch.location().equals(job.getLocation())) {
            job.setLocation(patch.location());
            changed = true;
        }
        if (patch.has("workMode") && !Objects.equals(patch.workMode(), job.getWorkMode())) {
            job.setWorkMode(patch.workMode());
            changed = true;
        }
        if (patch.has("salaryMin") && !Objects.equals(patch.salaryMin(), job.getSalaryMin())) {
            job.setSalaryMin(patch.salaryMin());
            changed = true;
        }
        if (patch.has("salaryMax") && !Objects.equals(patch.salaryMax(), job.getSalaryMax())) {
            job.setSalaryMax(patch.salaryMax());
            changed = true;
        }
        if (patch.has("applicationDeadline") && !Objects.equals(patch.applicationDeadline(), job.getApplicationDeadline())) {
            job.setApplicationDeadline(patch.applicationDeadline());
            changed = true;
        }
        if (patch.has("positions") && !Objects.equals(patch.positions(), job.getPositions())) {
            job.setPositions(patch.positions());
            changed = true;
        }
        return changed;
    }
}
