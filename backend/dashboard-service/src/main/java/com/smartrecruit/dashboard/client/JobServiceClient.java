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

    /** {@code closingSoon}: published offers whose deadline falls within 7 days (EXT-DASH-01), null from an older job-service. */
    public record JobStats(long total, long draft, long published, long closed, long moderated, Long closingSoon) {
    }

    private final RestClient client;

    public JobServiceClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.jobServiceUri(), signer, clock);
    }

    /** @param companyId restricts the counts to one company; null means platform-wide */
    public JobStats stats(UUID companyId) {
        return client.get()
                .uri(uri -> {
                    uri.path("/internal/v1/jobs/stats");
                    if (companyId != null) {
                        uri.queryParam("companyId", companyId);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(JobStats.class);
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
