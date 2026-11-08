package com.smartrecruit.user.ai;

import com.smartrecruit.user.repository.CandidateProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs the AI analysis of a freshly uploaded CV outside the upload request (BE-CV-01). It is
 * best-effort: on failure the upload stays valid, the status becomes FAILED and
 * {@code cvParsedAt} stays null (degraded mode).
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
     * Must be called after the upload committed. The result is recorded only if the profile still
     * points to the analysed CV, so a newer upload or a deleted account is never marked as analysed
     * (DONE with {@code cvParsedAt}) or failed.
     */
    @Async
    public void analyse(UUID candidateId, String storageKey, byte[] pdf) {
        if (!enabled) {
            return;
        }
        try {
            client.parse(candidateId, pdf);
        } catch (RuntimeException e) {
            log.warn("Analyse IA du CV du candidat {} indisponible, mode dégradé ({})", candidateId, e.getClass().getSimpleName());
            record(candidateId, () -> profileRepository.markAnalysisFailed(candidateId, storageKey));
            return;
        }
        record(candidateId, () -> profileRepository.markParsed(candidateId, storageKey, clock.instant()));
    }

    private void record(UUID candidateId, Supplier<Integer> update) {
        Integer updated = transaction.execute(status -> update.get());
        if (updated == null || updated == 0) {
            log.info("Résultat de l'analyse du CV du candidat {} ignoré : CV remplacé ou compte supprimé", candidateId);
        }
    }
}
