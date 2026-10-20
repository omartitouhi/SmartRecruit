package com.smartrecruit.application.client;

import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.exception.ApiException;
import com.smartrecruit.application.security.InternalIdentityVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** {@code POST /api/v1/ai/matching/score} of ai-service (AI-MATCH-04); the CV id is the candidate id. */
@Component
public class AiMatchingClient {

    private final RestClient client;

    public AiMatchingClient(ServiceEndpointsProperties endpoints, InternalIdentityVerifier signer, Clock clock) {
        this.client = ServiceRestClients.create(endpoints.aiServiceUri(), signer, clock, Duration.ofSeconds(15));
    }

    /** @throws ApiException 503 if ai-service fails, times out or answers nothing */
    public MatchScoreDto score(UUID candidateId, UUID jobId) {
        MatchScoreDto score;
        try {
            score = client.post().uri("/api/v1/ai/matching/score")
                    .body(Map.of("cvId", candidateId, "jobId", jobId))
                    .retrieve().body(MatchScoreDto.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai-service indisponible");
        }
        if (score == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai-service indisponible");
        }
        return score;
    }
}
