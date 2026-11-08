package com.smartrecruit.job.service;

import com.smartrecruit.job.client.ApplicationCountsClient;
import com.smartrecruit.job.client.CompanyDirectoryClient;
import com.smartrecruit.job.dto.JobResponse;
import com.smartrecruit.job.dto.JobSummaryResponse;
import com.smartrecruit.job.dto.PageResponse;
import com.smartrecruit.job.exception.ApiException;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import com.smartrecruit.job.repository.JobRepository;
import com.smartrecruit.job.repository.JobSpecifications;
import com.smartrecruit.job.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read side of the offers: search (BE-JOB-04) and a recruiter's own listing (BE-JOB-05). What a caller
 * may see depends on the role: candidates and recruiters browsing see published offers only, admins
 * see every status, a recruiter sees every status of their own company with {@code companyId=me}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobQueryService {

    private static final String ME = "me";

    /** Newest publication first; drafts (no publication date) last; id keeps pages stable. */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));

    private final JobRepository jobRepository;
    private final CompanyDirectoryClient companyClient;
    private final ApplicationCountsClient countsClient;
    private final RecruiterCompanies recruiterCompanies;
    private final TransactionTemplate transaction;
    private final SavedJobService savedJobs;

    public PageResponse<JobSummaryResponse> search(AuthenticatedUser user, JobSearchCriteria criteria, int page, int size) {
        boolean mine = ME.equalsIgnoreCase(criteria.companyId() == null ? "" : criteria.companyId().strip());
        Specification<Job> scope;
        if (mine) {
            Optional<UUID> company = ownCompanyId(user);
            if (company.isEmpty()) {
                return new PageResponse<>(List.of(), page, size, 0, 0);
            }
            scope = JobSpecifications.ownedBy(company.get());
            if (criteria.status() != null) {
                scope = scope.and(JobSpecifications.hasStatus(criteria.status()));
            }
        } else {
            scope = visibility(user, criteria);
        }
        Specification<Job> filter = filters(criteria).and(scope);
        // Admins and a company's own listing may read why an offer was moderated.
        boolean withModerationReason = mine || "ADMIN".equals(user.role());
        Page<JobResponse> found = transaction.execute(status ->
                jobRepository.findAll(filter, PageRequest.of(page, size, NEWEST_FIRST))
                        .map(job -> JobResponse.from(job, withModerationReason)));

        Map<UUID, String> companyNames = companyNames(found.getContent());
        Map<UUID, Long> counts = mine ? applicationCounts(found.getContent()) : null;
        Set<UUID> saved = savedJobIds(user, found.getContent());
        return PageResponse.of(found, job -> new JobSummaryResponse(job, companyNames.get(job.getCompanyId()),
                counts == null ? null : counts.getOrDefault(job.getId(), 0L),
                saved == null ? null : saved.contains(job.getId())));
    }

    /**
     * One offer. Published offers are public; any other status exists only for admins and the owning
     * company, everybody else gets 404 as if it did not exist.
     */
    public JobSummaryResponse get(AuthenticatedUser user, UUID jobId) {
        // The reason is only ever present on non-published offers, which are only served to owner/admin below.
        JobResponse job = transaction.execute(status -> jobRepository.findById(jobId)
                        .map(found -> JobResponse.from(found, found.getStatus() != JobStatus.PUBLISHED)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (job.getStatus() != JobStatus.PUBLISHED && !mayViewUnpublished(user, job)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Offre introuvable");
        }
        Set<UUID> saved = savedJobIds(user, List.of(job));
        return new JobSummaryResponse(job, companyNames(List.of(job)).get(job.getCompanyId()), null,
                saved == null ? null : saved.contains(job.getId()));
    }

    /** EXT-SAVED-01: which of the offers the candidate bookmarked; null for other roles (no {@code saved} flag). */
    private Set<UUID> savedJobIds(AuthenticatedUser user, List<JobResponse> jobs) {
        if (!"CANDIDATE".equals(user.role())) {
            return null;
        }
        return savedJobs.savedAmong(user.id(), jobs.stream().map(JobResponse::getId).toList());
    }

    private boolean mayViewUnpublished(AuthenticatedUser user, JobResponse job) {
        return switch (user.role()) {
            case "ADMIN" -> true;
            case "RECRUITER" -> recruiterCompanies.companyIdOf(user.id()).filter(job.getCompanyId()::equals).isPresent();
            default -> false;
        };
    }

    /** {@code companyId=me} only makes sense for a recruiter; empty if they have no company yet. */
    private Optional<UUID> ownCompanyId(AuthenticatedUser user) {
        if (!"RECRUITER".equals(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "companyId=me est réservé aux recruteurs");
        }
        return recruiterCompanies.companyIdOf(user.id());
    }

    private Specification<Job> visibility(AuthenticatedUser user, JobSearchCriteria criteria) {
        Specification<Job> visible = Specification.unrestricted();
        boolean admin = "ADMIN".equals(user.role());
        if (admin && criteria.status() != null) {
            visible = visible.and(JobSpecifications.hasStatus(criteria.status()));
        } else if (!admin) {
            visible = visible.and(JobSpecifications.hasStatus(JobStatus.PUBLISHED));
        }
        if (criteria.companyId() != null && !criteria.companyId().isBlank()) {
            visible = visible.and(JobSpecifications.ownedBy(parseCompanyId(criteria.companyId())));
        }
        return visible;
    }

    private Specification<Job> filters(JobSearchCriteria criteria) {
        Specification<Job> spec = Specification.unrestricted();
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            spec = spec.and(JobSpecifications.mentions(criteria.keyword()));
        }
        if (criteria.location() != null && !criteria.location().isBlank()) {
            spec = spec.and(JobSpecifications.locatedIn(criteria.location()));
        }
        List<String> skills = cleaned(criteria.skills());
        if (!skills.isEmpty()) {
            spec = spec.and(JobSpecifications.requiresAnyOf(skills));
        }
        if (criteria.contractType() != null) {
            spec = spec.and(JobSpecifications.hasContractType(criteria.contractType()));
        }
        if (criteria.experienceLevel() != null) {
            spec = spec.and(JobSpecifications.hasExperienceLevel(criteria.experienceLevel()));
        }
        if (criteria.workMode() != null) {
            spec = spec.and(JobSpecifications.hasWorkMode(criteria.workMode()));
        }
        if (criteria.minSalary() != null) {
            spec = spec.and(JobSpecifications.paysAtLeast(criteria.minSalary()));
        }
        return spec;
    }

    private static List<String> cleaned(List<String> skills) {
        return skills == null ? List.of()
                : skills.stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static UUID parseCompanyId(String companyId) {
        try {
            return UUID.fromString(companyId.strip());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "companyId doit être un identifiant d'entreprise valide");
        }
    }

    /** Company names are a nice-to-have: without user-service the page is still returned. */
    private Map<UUID, String> companyNames(List<JobResponse> jobs) {
        Set<UUID> companyIds = jobs.stream().map(JobResponse::getCompanyId).filter(Objects::nonNull).collect(Collectors.toSet());
        try {
            return companyClient.namesOf(companyIds);
        } catch (RestClientException e) {
            log.warn("user-service indisponible, offres renvoyées sans nom d'entreprise : {}", e.getMessage());
            return Map.of();
        }
    }

    /** Application counts are a nice-to-have: without application-service every offer shows 0. */
    private Map<UUID, Long> applicationCounts(List<JobResponse> jobs) {
        try {
            return countsClient.countsOf(jobs.stream().map(JobResponse::getId).toList());
        } catch (RestClientException e) {
            log.warn("application-service indisponible, nombre de candidatures mis à 0 : {}", e.getMessage());
            return Map.of();
        }
    }
}
