package com.smartrecruit.dashboard.service;

import com.smartrecruit.dashboard.client.JobServiceClient;
import com.smartrecruit.dashboard.client.Upstream;
import com.smartrecruit.dashboard.dto.AuditEventRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** BE-ADMIN-02 — job moderation, performed by job-service and audited here. */
@Service
@RequiredArgsConstructor
public class AdminJobService {

    private final JobServiceClient jobClient;
    private final AuditLogService auditLogService;

    public Map<String, Object> moderate(UUID actorId, UUID jobId, String reason) {
        Map<String, Object> job = Upstream.call(() -> jobClient.moderate(jobId, reason));
        auditLogService.record(new AuditEventRequest(actorId, "JOB_MODERATED", "JOB", jobId));
        return job;
    }
}
