package com.smartrecruit.application.model;

import com.smartrecruit.application.dto.MatchScoreDto;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * BE-MATCH-02: score of a (candidate, job) pair. Valid while the candidate's {@code cvParsedAt}
 * and the job's {@code updatedAt} are the ones it was computed from.
 */
@Entity
@Table(name = "match_score_cache", uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "job_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MatchScoreCacheEntry {

    @Id
    private UUID id = UUID.randomUUID();

    private UUID candidateId;

    private UUID jobId;

    private Instant cvParsedAt;

    private Instant jobUpdatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private MatchScoreDto matchScore;

    private Instant computedAt;
}
