package com.smartrecruit.dashboard.client;

import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** job-service internals ({@code /internal/v1/jobs}). Calls throw RestClientException on failure. */
@Component
public class JobServiceClient {

    private final RestClient client;

    public JobServiceClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.jobServiceUri(), signer, clock);
    }

    /** @return the moderated job exactly as job-service describes it */
    public Map<String, Object> moderate(UUID jobId, String reason) {
        return client.patch()
                .uri("/internal/v1/jobs/{id}/moderation", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", reason))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
