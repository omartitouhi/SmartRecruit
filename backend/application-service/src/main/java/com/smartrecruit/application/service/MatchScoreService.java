package com.smartrecruit.application.service;

import com.smartrecruit.application.client.AiMatchingClient;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.model.MatchScoreCacheEntry;
import com.smartrecruit.application.repository.MatchScoreCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** BE-MATCH-01/02: the score of a candidate for a job, from the cache when its inputs are unchanged. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchScoreService {

    private final MatchScoreCacheRepository cache;
    private final AiMatchingClient ai;
    private final Clock clock;

    /**
     * @param cvParsedAt   when the candidate's CV was last analysed (user-service)
     * @param jobUpdatedAt when the job was last modified (job-service)
     * @throws com.smartrecruit.application.exception.ApiException 503 if the cache misses and ai-service fails
     */
    public MatchScoreDto scoreFor(UUID candidateId, UUID jobId, Instant cvParsedAt, Instant jobUpdatedAt) {
        var cached = cache.findByCandidateIdAndJobId(candidateId, jobId);
        if (cached.isPresent() && sameInstant(cached.get().getCvParsedAt(), cvParsedAt)
                && sameInstant(cached.get().getJobUpdatedAt(), jobUpdatedAt)) {
            return cached.get().getMatchScore();
        }
        MatchScoreDto score = ai.score(candidateId, jobId);
        MatchScoreCacheEntry entry = cached.orElseGet(MatchScoreCacheEntry::new);
        entry.setCandidateId(candidateId);
        entry.setJobId(jobId);
        entry.setCvParsedAt(truncate(cvParsedAt));
        entry.setJobUpdatedAt(truncate(jobUpdatedAt));
        entry.setMatchScore(score);
        entry.setComputedAt(clock.instant());
        try {
            cache.save(entry);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent match score computation for the same pair, keeping the other entry");
        }
        return score;
    }

    /** Databases keep microseconds (and may round), JSON carries nanoseconds: store and compare truncated. */
    private static boolean sameInstant(Instant stored, Instant current) {
        return Objects.equals(truncate(stored), truncate(current));
    }

    private static Instant truncate(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }
}
