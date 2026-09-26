package com.smartrecruit.dashboard.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Field-for-field match of the {@code AuditLogEntry} entity in {@code DATA_MODEL.md} (§3.5).
 * {@code action} is a free-form code (e.g. {@code CV_VIEWED}, {@code USER_DISABLED}) —
 * keep the vocabulary consistent across services that emit audit entries.
 */
@Entity
@Table(name = "audit_log_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    private UUID id;

    private UUID actorId;

    private String action;

    private String resourceType;

    private UUID resourceId;

    private Instant timestamp;
}
