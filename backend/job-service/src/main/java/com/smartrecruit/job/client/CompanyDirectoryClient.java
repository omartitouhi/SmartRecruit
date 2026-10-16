package com.smartrecruit.job.client;

import com.smartrecruit.job.security.InternalIdentityVerifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Company names from user-service ({@code GET /internal/v1/companies?ids=...}).
 */
@Component
public class CompanyDirectoryClient {

    public record CompanySummary(UUID id, String name, String logoUrl) {
    }

    private final RestClient client;

    public CompanyDirectoryClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.userServiceUri(), signer, clock);
    }

    /**
     * @return company name by id; unknown ids are absent
     * @throws org.springframework.web.client.RestClientException if user-service fails or is unreachable
     */
    public Map<UUID, String> namesOf(Collection<UUID> companyIds) {
        if (companyIds.isEmpty()) {
            return Map.of();
        }
        List<CompanySummary> companies = client.get()
                .uri(uri -> uri.path("/internal/v1/companies").queryParam("ids", companyIds.toArray()).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (companies == null) {
            return Map.of();
        }
        return companies.stream()
                .filter(company -> company.name() != null)
                .collect(Collectors.toMap(CompanySummary::id, CompanySummary::name, (first, second) -> first));
    }
}
