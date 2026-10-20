package com.smartrecruit.application.service;

import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * BE-MATCH-01: computes the match score of a freshly committed application off the request thread.
 * It is best effort: on failure {@code matchScore} stays null and the application remains valid.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationScoring {

    private final MatchScoreService matchScores;
    private final ApplicationRepository applications;
    private final TransactionTemplate transaction;

    @Async
    public void scoreInBackground(UUID applicationId, UUID candidateId, UUID jobId, Instant cvParsedAt, Instant jobUpdatedAt) {
        try {
            MatchScoreDto score = matchScores.scoreFor(candidateId, jobId, cvParsedAt, jobUpdatedAt);
            transaction.executeWithoutResult(status ->
                    applications.findById(applicationId).ifPresent(application -> application.setMatchScore(score)));
        } catch (RuntimeException e) {
            log.warn("Match score unavailable for application {}: {}", applicationId, e.getMessage());
        }
    }
}
