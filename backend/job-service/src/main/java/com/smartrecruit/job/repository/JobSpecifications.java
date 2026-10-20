package com.smartrecruit.job.repository;

import com.smartrecruit.job.model.ContractType;
import com.smartrecruit.job.model.ExperienceLevel;
import com.smartrecruit.job.model.Job;
import com.smartrecruit.job.model.JobStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * Filters of the offer search (BE-JOB-04). Text matches are case-insensitive "contains" and the
 * user's text is escaped so {@code %} and {@code _} are matched literally.
 */
public final class JobSpecifications {

    private static final char ESCAPE = '\\';

    private JobSpecifications() {
    }

    public static Specification<Job> hasStatus(JobStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Job> ownedBy(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }

    public static Specification<Job> hasContractType(ContractType contractType) {
        return (root, query, cb) -> cb.equal(root.get("contractType"), contractType);
    }

    public static Specification<Job> hasExperienceLevel(ExperienceLevel experienceLevel) {
        return (root, query, cb) -> cb.equal(root.get("experienceLevel"), experienceLevel);
    }

    /** Title or description contains the keyword. */
    public static Specification<Job> mentions(String keyword) {
        return (root, query, cb) -> {
            String pattern = contains(keyword);
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("description")), pattern, ESCAPE));
        };
    }

    public static Specification<Job> locatedIn(String location) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("location")), contains(location), ESCAPE);
    }

    /** The offer requires at least one of the skills (case-insensitive). */
    public static Specification<Job> requiresAnyOf(Collection<String> skills) {
        return (root, query, cb) -> {
            Subquery<UUID> matching = query.subquery(UUID.class);
            Root<Job> job = matching.from(Job.class);
            Join<Job, String> skill = job.join("requiredSkills");
            Expression<String> lowered = cb.lower(skill);
            matching.select(job.get("id")).where(lowered.in(skills.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList()));
            return root.get("id").in(matching);
        };
    }

    private static String contains(String text) {
        String escaped = text.toLowerCase(Locale.ROOT).strip()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
