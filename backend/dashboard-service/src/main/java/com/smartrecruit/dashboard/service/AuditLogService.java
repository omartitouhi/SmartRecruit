package com.smartrecruit.dashboard.service;

import com.smartrecruit.dashboard.dto.AuditEventRequest;
import com.smartrecruit.dashboard.model.AuditLogEntry;
import com.smartrecruit.dashboard.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Who did what and when on sensitive data (BE-ADMIN-03). */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditLogEntry record(AuditEventRequest event) {
        return repository.save(AuditLogEntry.builder()
                .actorId(event.actorId())
                .action(event.action())
                .resourceType(event.resourceType())
                .resourceId(event.resourceId())
                .timestamp(clock.instant())
                .build());
    }
}
