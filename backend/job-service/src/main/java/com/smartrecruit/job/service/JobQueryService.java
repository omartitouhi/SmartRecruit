package com.smartrecruit.job.service;

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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read side of the offers: search (BE-JOB-04). What a caller may see depends on the role: candidates
 * and recruiters browsing see published offers only, admins see every status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobQueryService {

    /** Newest publication first; drafts (no publication date) last; id keeps pages stable. */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));

    private final JobRepository jobRepository;
    private final CompanyDirectoryClient companyClient;
    private final TransactionTemplate transaction;

    public PageResponse<JobSummaryResponse> search(AuthenticatedUser user, JobSearchCriteria criteria, int page, int size) {
        Specification<Job> filter = filters(criteria).and(visibility(user, criteria));
        Page<JobResponse> found = transaction.execute(status ->
                jobRepository.findAll(filter, PageRequest.of(page, size, NEWEST_FIRST)).map(JobResponse::from));

        Map<UUID, String> companyNames = companyNames(found.getContent());
        return PageResponse.of(found, job -> new JobSummaryResponse(job, companyNames.get(job.getCompanyId()), null));
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
}
