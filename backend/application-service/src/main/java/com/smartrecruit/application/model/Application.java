package com.smartrecruit.application.model;

import com.smartrecruit.application.dto.MatchScoreDto;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Field-for-field match of the {@code Application} entity in {@code DATA_MODEL.md} (§3.4).
 * {@code jobId} and {@code candidateId} are logical FKs toward job-service and
 * user-service respectively.
 *
 * {@code matchScore} is stored as the raw JSON body returned by
 * {@code POST /api/v1/ai/matching/score} (see {@link MatchScoreDto}) in a JSON column;
 * it stays null until the AI score is computed (BE-MATCH-01).
 */
@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    private UUID id;

    private UUID jobId;

    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    private MatchScoreDto matchScore;

    private Instant appliedAt;

    @ElementCollection
    @CollectionTable(name = "application_status_history", joinColumns = @JoinColumn(name = "application_id"))
    @Builder.Default
    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();
}
