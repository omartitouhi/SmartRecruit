package com.smartrecruit.user.audit;

import com.smartrecruit.user.config.ServicesProperties;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * Tells dashboard-service that a recruiter or admin looked at candidate data. Best effort: asynchronous,
 * and a failure is logged without personal data and never reaches the user's request.
 */
@Slf4j
@Component
public class AuditEmitter {

    private static final String RESOURCE_TYPE = "CANDIDATE";

    private record AuditEvent(UUID actorId, AuditAction action, String resourceType, UUID resourceId) {
    }

    private final RestClient dashboardService;
    private final InternalIdentityVerifier signer;

    public AuditEmitter(ServicesProperties properties, InternalIdentityVerifier signer) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.dashboardService = RestClient.builder().baseUrl(properties.dashboardServiceUri())
                .requestFactory(requestFactory).build();
        this.signer = signer;
    }

    @Async
    public void emit(UUID actorId, AuditAction action, UUID candidateId) {
        try {
            dashboardService.post()
                    .uri("/internal/v1/audit-events")
                    .headers(signer::addServiceIdentity)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AuditEvent(actorId, action, RESOURCE_TYPE, candidateId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Événement d'audit {} non transmis à dashboard-service ({})", action, e.getClass().getSimpleName());
        }
    }
}
