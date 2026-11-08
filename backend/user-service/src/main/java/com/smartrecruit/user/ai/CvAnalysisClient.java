package com.smartrecruit.user.ai;

import com.smartrecruit.user.gdpr.GdprErasureProperties;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends an uploaded CV to ai-service for parsing (BE-CV-01). The AI stores the result itself, keyed by
 * candidate. Requests carry the same signed {@code SERVICE} identity as the erasure calls.
 */
@Component
public class CvAnalysisClient {

    private final RestClient aiService;
    private final InternalIdentityVerifier signer;

    public CvAnalysisClient(GdprErasureProperties properties, InternalIdentityVerifier signer) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20)); // LLM call: typically 3-10 s
        this.aiService = RestClient.builder().baseUrl(properties.aiServiceUri()).requestFactory(requestFactory).build();
        this.signer = signer;
    }

    /** Any non-2xx answer or I/O failure throws. */
    public void parse(UUID candidateId, byte[] pdf) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "cv.pdf";
            }
        }, fileHeaders));
        body.add("candidateId", candidateId.toString());
        aiService.post()
                .uri("/api/v1/ai/cv-parsing/parse")
                .headers(signer::addServiceIdentity)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Last parsing result stored by the AI for this candidate, or empty if none (404). Any other
     * failure (I/O, 5xx) throws a {@link org.springframework.web.client.RestClientException}.
     */
    public Optional<ParsedCv> findParsedCv(UUID candidateId) {
        try {
            return Optional.ofNullable(aiService.get()
                    .uri("/api/v1/ai/cv-parsing/candidates/{id}", candidateId)
                    .headers(signer::addServiceIdentity)
                    .retrieve()
                    .body(ParsedCv.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
