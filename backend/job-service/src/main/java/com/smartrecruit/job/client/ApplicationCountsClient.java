package com.smartrecruit.job.client;

import com.smartrecruit.job.security.InternalIdentityVerifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Applications per offer from application-service
 * ({@code GET /internal/v1/applications/counts?jobIds=a,b}, withdrawn ones excluded).
 */
@Component
public class ApplicationCountsClient {

    private final RestClient client;

    public ApplicationCountsClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.applicationServiceUri(), signer, clock);
    }

    /**
     * @return count by job id; offers without applications may be absent
     * @throws org.springframework.web.client.RestClientException if application-service fails or is unreachable
     */
    public Map<UUID, Long> countsOf(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        String ids = jobIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        Map<UUID, Long> counts = client.get()
                .uri(uri -> uri.path("/internal/v1/applications/counts").queryParam("jobIds", ids).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return counts == null ? Map.of() : counts;
    }
}
