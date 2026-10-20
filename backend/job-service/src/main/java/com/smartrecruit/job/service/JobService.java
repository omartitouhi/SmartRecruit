package com.smartrecruit.job.service;

import com.smartrecruit.job.dto.JobCreateRequest;
import com.smartrecruit.job.dto.JobPatchRequest;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
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
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;

    /** Publishes an offer for the recruiter's company straight away. */
    public JobResponse publish(UUID recruiterId, JobCreateRequest request) {
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
        return changed;
    }
}
