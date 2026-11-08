package com.smartrecruit.application.client;

import com.smartrecruit.application.security.InternalIdentityVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** {@code POST /internal/v1/audit-events} of dashboard-service. Best effort: never fails the caller. */
@Component
@Slf4j
public class AuditClient {

    private final RestClient client;

    public AuditClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.dashboardServiceUri(), signer, clock, Duration.ofSeconds(5));
    }

    @Async
    public void record(UUID actorId, String action, String resourceType, UUID resourceId) {
        try {
            client.post().uri("/internal/v1/audit-events")
                    .body(Map.of("actorId", actorId, "action", action, "resourceType", resourceType, "resourceId", resourceId))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Audit event {} not delivered: {}", action, e.getMessage());
        }
    }
}
