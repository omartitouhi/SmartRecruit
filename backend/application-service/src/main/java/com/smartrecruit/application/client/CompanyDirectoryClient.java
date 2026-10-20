package com.smartrecruit.application.client;

import com.smartrecruit.application.security.InternalIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** {@code GET /internal/v1/companies/by-member/{accountId}} of user-service. */
@Component
public class CompanyDirectoryClient {

    public record CompanySummary(UUID id, String name, String logoUrl) {
    }

    private final RestClient client;

    public CompanyDirectoryClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.userServiceUri(), signer, clock, Duration.ofSeconds(10));
    }

    /** @return the recruiter's company, empty if they have none; @throws com.smartrecruit.application.exception.ApiException 503 if user-service fails */
    public Optional<CompanySummary> findByMember(UUID accountId) {
        return ServiceRestClients.find(client, CompanySummary.class, "user-service",
                "/internal/v1/companies/by-member/{accountId}", accountId);
    }
}
