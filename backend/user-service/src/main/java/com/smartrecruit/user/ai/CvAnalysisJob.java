package com.smartrecruit.user.ai;

import com.smartrecruit.user.repository.CandidateProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Runs the AI analysis of a freshly uploaded CV outside the upload request (BE-CV-01). It is
 * best-effort: on failure the upload stays valid and {@code cvParsedAt} stays null (degraded mode).
 */
@Slf4j
@Component
public class CvAnalysisJob {

    private final CvAnalysisClient client;
    private final CandidateProfileRepository profileRepository;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final boolean enabled;

    public CvAnalysisJob(CvAnalysisClient client, CandidateProfileRepository profileRepository,
                         TransactionTemplate transaction, Clock clock,
                         @Value("${cv-analysis.enabled:true}") boolean enabled) {
        this.client = client;
        this.profileRepository = profileRepository;
        this.transaction = transaction;
        this.clock = clock;
        this.enabled = enabled;
    }

    /**
     * Must be called after the upload committed. {@code cvParsedAt} is set only if the profile still
     * points to the analysed CV, so a newer upload or a deleted account is never marked as analysed.
     */
    @Async
    public void analyse(UUID candidateId, String storageKey, byte[] pdf) {
        if (!enabled) {
            return;
        }
        try {
            client.parse(candidateId, pdf);
            Integer updated = transaction.execute(status ->
                    profileRepository.markParsed(candidateId, storageKey, clock.instant()));
            if (updated == null || updated == 0) {
                log.info("Analyse du CV du candidat {} ignorée : CV remplacé ou compte supprimé", candidateId);
            }
        } catch (RuntimeException e) {
            log.warn("Analyse IA du CV du candidat {} indisponible, mode dégradé ({})", candidateId, e.getClass().getSimpleName());
        }
    }
}
