package com.smartrecruit.job.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Field-for-field match of the {@code Job} entity in {@code DATA_MODEL.md} (§3.3).
 * {@code companyId} is a logical FK toward {@code Company} in user-service —
 * no JPA relationship crosses the service boundary.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    private UUID id;

    private UUID companyId;

    private String title;

    /**
     * A plain varchar rather than a {@code @Lob}: PostgreSQL maps a Hibernate LOB to {@code oid}, on
     * which keyword search ({@code lower(..) like ..}) cannot run. 20000 is the API limit.
     */
    @Column(length = 20000)
    private String description;

    /** Alimenté manuellement, croisé avec l'extraction IA (AI-JOB-01). */
    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "job_required_skills", joinColumns = @JoinColumn(name = "job_id"))
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    private ContractType contractType;

    private String location;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private Instant publishedAt;

    /** Last change of the offer; also tells downstream caches (match preview) when to recompute. */
    private Instant updatedAt;

    /** Why an admin withdrew the offer (BE-ADMIN-02); set together with {@code moderatedAt}. */
    @Column(length = 500)
    private String moderationReason;

    private Instant moderatedAt;

    /** On site, hybrid or remote (EXT-JOB-01); null on rows created before the field, read as ON_SITE. */
    @Enumerated(EnumType.STRING)
    private WorkMode workMode;

    /** Gross monthly salary range in TND (EXT-JOB-01), both optional. */
    private Integer salaryMin;

    private Integer salaryMax;

    /** Last day (inclusive, UTC) to apply (EXT-JOB-01); the offer is closed automatically afterwards. */
    private LocalDate applicationDeadline;

    /** Number of open positions (EXT-JOB-01); null on rows created before the field, read as 1. */
    private Integer positions;

    /** @return true when the deadline exists and {@code today} is after it */
    public boolean isPastDeadline(LocalDate today) {
        return applicationDeadline != null && today.isAfter(applicationDeadline);
    }
}
