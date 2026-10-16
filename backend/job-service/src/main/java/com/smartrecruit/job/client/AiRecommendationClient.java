package com.smartrecruit.job.client;

import com.smartrecruit.job.security.InternalIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/ai/recommendation/candidates/{id}/jobs} of ai-service (AI-REC-01),
 * contract in {@code DATA_MODEL.md} §4.7.
 */
@Component
public class AiRecommendationClient {

    public record AiRecommendation(UUID jobId, double score) {
    }

    public record AiRecommendations(List<AiRecommendation> recommendations) {
    }

    private final RestClient client;

    public AiRecommendationClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.aiServiceUri(), signer, clock);
    }

    /** @throws org.springframework.web.client.RestClientException if ai-service fails or is unreachable */
    public List<AiRecommendation> recommendJobs(UUID candidateId) {
        AiRecommendations response = client.get()
                .uri("/api/v1/ai/recommendation/candidates/{id}/jobs", candidateId)
                .retrieve()
                .body(AiRecommendations.class);
        return response == null || response.recommendations() == null ? List.of() : response.recommendations();
    }
}
