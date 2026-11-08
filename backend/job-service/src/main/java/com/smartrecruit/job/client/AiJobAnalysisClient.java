package com.smartrecruit.job.client;

import com.smartrecruit.job.security.InternalIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.UUID;

/**
 * {@code POST /api/v1/ai/job-analysis/analyze} of ai-service (AI-JOB-01): asks the AI to analyze an
 * offer so it can be matched against CVs. The result is kept by ai-service, job-service ignores it.
 */
@Component
public class AiJobAnalysisClient {

    record AnalyzeRequest(UUID jobId, String title, String description) {
    }

    private final RestClient client;

    public AiJobAnalysisClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.aiServiceUri(), signer, clock);
    }

    /** @throws org.springframework.web.client.RestClientException if ai-service fails or is unreachable */
    public void analyze(UUID jobId, String title, String description) {
        client.post()
                .uri("/api/v1/ai/job-analysis/analyze")
                .body(new AnalyzeRequest(jobId, title, description))
                .retrieve()
                .toBodilessEntity();
    }
}
