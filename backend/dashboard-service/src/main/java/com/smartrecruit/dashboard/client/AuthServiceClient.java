package com.smartrecruit.dashboard.client;

import com.smartrecruit.dashboard.dto.AccountResponse;
import com.smartrecruit.dashboard.dto.PageResponse;
import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** auth-service internals ({@code /internal/v1/accounts}). Calls throw RestClientException on failure. */
@Component
public class AuthServiceClient {

    public record AccountStats(long total, Map<String, Long> byRole, Map<String, Long> byStatus) {
    }

    private final RestClient client;

    public AuthServiceClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.authServiceUri(), signer, clock);
    }

    public PageResponse<AccountResponse> list(String role, String status, int page, int size) {
        return client.get()
                .uri(uri -> {
                    uri.path("/internal/v1/accounts").queryParam("page", page).queryParam("size", size);
                    if (role != null && !role.isBlank()) {
                        uri.queryParam("role", role);
                    }
                    if (status != null && !status.isBlank()) {
                        uri.queryParam("status", status);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public AccountResponse updateStatus(UUID accountId, String status) {
        return client.patch()
                .uri("/internal/v1/accounts/{id}/status", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status))
                .retrieve()
                .body(AccountResponse.class);
    }

    public AccountStats stats() {
        return client.get().uri("/internal/v1/accounts/stats").retrieve().body(AccountStats.class);
    }
}
