package com.smartrecruit.user.gdpr;

import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Calls the services that hold personal data of a candidate to erase it (BE-SEC-02). Requests carry
 * a signed {@code SERVICE} identity, the same mechanism the api-gateway uses (BE-SEC-04).
 */
@Component
public class ErasureClient {

    /** Fixed identity of backend-to-backend calls. */
    static final UUID SERVICE_ID = new UUID(0, 0);
    static final String SERVICE_ROLE = "SERVICE";

    private final RestClient authService;
    private final RestClient aiService;
    private final InternalIdentityVerifier signer;
    private final Clock clock;

    public ErasureClient(GdprErasureProperties properties, InternalIdentityVerifier signer, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.authService = RestClient.builder().baseUrl(properties.authServiceUri()).requestFactory(requestFactory).build();
        this.aiService = RestClient.builder().baseUrl(properties.aiServiceUri()).requestFactory(requestFactory).build();
        this.signer = signer;
        this.clock = clock;
    }

    /** Anonymizes the account in auth-service. Any non-2xx answer throws. */
    public void eraseAccount(UUID accountId) {
        delete(authService, "/internal/v1/accounts/{id}", accountId);
    }

    /**
     * Removes CV parsing results, embeddings and matching data from ai-service. Contract documented in
     * {@code DATA_MODEL.md} §4.6. Any non-2xx answer throws.
     */
    public void eraseAiData(UUID candidateId) {
        delete(aiService, "/api/v1/ai/candidates/{id}/data", candidateId);
    }

    private void delete(RestClient client, String path, UUID id) {
        long now = clock.instant().getEpochSecond();
        client.delete()
                .uri(path, id)
                .header(InternalIdentityVerifier.USER_ID, SERVICE_ID.toString())
                .header(InternalIdentityVerifier.USER_ROLE, SERVICE_ROLE)
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                .header(InternalIdentityVerifier.SIGNATURE, signer.sign(SERVICE_ID.toString(), SERVICE_ROLE, now))
                .retrieve()
                .toBodilessEntity();
    }
}
