package com.smartrecruit.dashboard.client;

import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** user-service internals ({@code /internal/v1}). Calls throw RestClientException on failure. */
@Component
public class UserServiceClient {

    public record CompanySummary(UUID id, String name, String logoUrl) {
    }

    private final RestClient client;

    public UserServiceClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.userServiceUri(), signer, clock);
    }

    /** @return the company the account belongs to, empty when it has none */
    public Optional<CompanySummary> companyOfMember(UUID accountId) {
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
