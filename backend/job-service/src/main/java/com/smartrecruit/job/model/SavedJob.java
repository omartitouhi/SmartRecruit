package com.smartrecruit.job.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An offer a candidate bookmarked (EXT-SAVED-01). {@code candidateId} is the candidate's account id,
 * {@code jobId} a job of this service; one row per couple.
 */
@Entity
@Table(name = "saved_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_job_candidate_job", columnNames = {"candidateId", "jobId"}),
        indexes = @Index(name = "idx_saved_job_candidate", columnList = "candidateId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedJob {

    @Id
    private UUID id;

    private UUID candidateId;

    private UUID jobId;

    private Instant savedAt;
}
