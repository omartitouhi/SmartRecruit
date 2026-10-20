package com.smartrecruit.job.client;

import com.smartrecruit.job.security.InternalIdentityVerifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Company data from user-service: names by id ({@code GET /internal/v1/companies?ids=...}) and the
 * company of a recruiter ({@code GET /internal/v1/companies/by-member/{accountId}}).
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

    /**
     * @return the company the account belongs to, empty if it has none (404)
     * @throws org.springframework.web.client.RestClientException if user-service fails or is unreachable
     */
    public Optional<CompanySummary> companyOf(UUID accountId) {
        try {
            return Optional.ofNullable(client.get()
                    .uri("/internal/v1/companies/by-member/{accountId}", accountId)
                    .retrieve()
                    .body(CompanySummary.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
