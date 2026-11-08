package com.smartrecruit.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors the {@code statusHistory} entry shape in {@code DATA_MODEL.md} §3.4
 * (used for the audit trail — BE-ADMIN-03). {@code message} is the explanation addressed to the
 * candidate when a recruiter changes the status (EXT-APP-01).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusHistoryEntry {

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private Instant changedAt;

    private UUID changedBy;

    /** Explanation for the candidate (≤ 2000), null when none was given (EXT-APP-01). */
    @Column(length = 2000)
    private String message;

    public StatusHistoryEntry(ApplicationStatus status, Instant changedAt, UUID changedBy) {
        this(status, changedAt, changedBy, null);
    }
}
