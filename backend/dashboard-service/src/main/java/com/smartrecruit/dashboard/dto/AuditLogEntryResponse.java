package com.smartrecruit.dashboard.dto;

import com.smartrecruit.dashboard.model.AuditLogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntryResponse {

    private UUID id;
    private UUID actorId;
    private String action;
    private String resourceType;
    private UUID resourceId;
    private Instant timestamp;

    public static AuditLogEntryResponse from(AuditLogEntry entry) {
        return AuditLogEntryResponse.builder()
                .id(entry.getId())
                .actorId(entry.getActorId())
                .action(entry.getAction())
                .resourceType(entry.getResourceType())
                .resourceId(entry.getResourceId())
                .timestamp(entry.getTimestamp())
                .build();
    }
}
