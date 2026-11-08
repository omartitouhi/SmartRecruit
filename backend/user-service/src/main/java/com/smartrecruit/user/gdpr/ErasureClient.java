package com.smartrecruit.user.gdpr;

import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * Calls the services that hold personal data of a candidate to erase it (BE-SEC-02). Requests carry
 * a signed {@code SERVICE} identity, the same mechanism the api-gateway uses (BE-SEC-04).
 */
@Component
public class ErasureClient {

    private final RestClient authService;
    private final RestClient aiService;
    private final RestClient jobService;
    private final RestClient applicationService;
    private final InternalIdentityVerifier signer;

    public ErasureClient(GdprErasureProperties properties, InternalIdentityVerifier signer) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.authService = RestClient.builder().baseUrl(properties.authServiceUri()).requestFactory(requestFactory).build();
        this.aiService = RestClient.builder().baseUrl(properties.aiServiceUri()).requestFactory(requestFactory).build();
        this.jobService = RestClient.builder().baseUrl(properties.jobServiceUri()).requestFactory(requestFactory).build();
        this.applicationService = RestClient.builder().baseUrl(properties.applicationServiceUri())
                .requestFactory(requestFactory).build();
        this.signer = signer;
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

    /** Removes the candidate's saved offers from job-service (EXT-SAVED-01). Any non-2xx answer throws. */
    public void eraseSavedJobs(UUID candidateId) {
        delete(jobService, "/internal/v1/candidates/{id}/saved-jobs", candidateId);
    }

    /**
     * Removes cover letters and recruiter notes about the candidate from application-service
     * (EXT-APP-01, EXT-APP-03). Any non-2xx answer throws.
     */
    public void eraseApplicationData(UUID candidateId) {
        delete(applicationService, "/internal/v1/candidates/{id}/data", candidateId);
    }

    private void delete(RestClient client, String path, UUID id) {
        client.delete()
                .uri(path, id)
                .headers(signer::addServiceIdentity)
                .retrieve()
                .toBodilessEntity();
    }
}
