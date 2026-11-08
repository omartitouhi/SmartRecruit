package com.smartrecruit.application.client;

import com.smartrecruit.application.security.InternalIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@code GET /internal/v1/candidates/{id}/readiness} of user-service. */
@Component
public class CandidateReadinessClient {

    public record Readiness(boolean profileComplete, boolean hasCv, String cvAnalysisStatus, Instant cvParsedAt) {
    }

    private final RestClient client;

    public CandidateReadinessClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.userServiceUri(), signer, clock, Duration.ofSeconds(10));
    }

    /** @return empty when the candidate has no profile; @throws com.smartrecruit.application.exception.ApiException 503 if user-service fails */
    public Optional<Readiness> find(UUID candidateId) {
        return ServiceRestClients.find(client, Readiness.class, "user-service",
                "/internal/v1/candidates/{id}/readiness", candidateId);
    }
}
