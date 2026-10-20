package com.smartrecruit.application.client;

import com.smartrecruit.application.security.InternalIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@code GET /internal/v1/jobs/{id}} of job-service. */
@Component
public class JobDirectoryClient {

    public record JobSummary(UUID id, UUID companyId, String title, String status, Instant updatedAt) {
    }

    private final RestClient client;

    public JobDirectoryClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.jobServiceUri(), signer, clock, Duration.ofSeconds(10));
    }

    /** @throws com.smartrecruit.application.exception.ApiException 503 if job-service fails */
    public Optional<JobSummary> find(UUID jobId) {
        return ServiceRestClients.find(client, JobSummary.class, "job-service", "/internal/v1/jobs/{id}", jobId);
    }
}
