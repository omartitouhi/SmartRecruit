package com.smartrecruit.job.service;

import com.smartrecruit.job.client.AiJobAnalysisClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort AI analysis of an offer, on its own thread and only after the commit: a slow or failing
 * ai-service never delays or breaks the recruiter's request. An analysis that fails is retried every
 * {@code jobs.analysis.retry-interval} until ai-service answers, otherwise the offer could never be scored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobAnalysisListener {

    private final AiJobAnalysisClient aiClient;

    // shortcut: kept in memory, lost if job-service restarts during an ai-service outage; persist a flag on Job if that matters.
    private final Map<UUID, JobAnalysisRequested> pending = new ConcurrentHashMap<>();

    @Async
    @TransactionalEventListener
    public void analyze(JobAnalysisRequested event) {
        if (!send(event)) {
            pending.put(event.jobId(), event); // the latest content of the offer wins
            log.warn("Analyse IA de l'offre {} indisponible, l'offre reste valide ; nouvel essai planifié", event.jobId());
        }
    }

    /** Runs only while scheduling is enabled ({@code JobExpiryScheduler}, off in tests which call it directly). */
    @Scheduled(initialDelayString = "${jobs.analysis.retry-interval:1m}", fixedDelayString = "${jobs.analysis.retry-interval:1m}")
    public void retryPending() {
        pending.values().forEach(event -> {
            if (send(event)) {
                pending.remove(event.jobId(), event);
                log.info("Analyse IA de l'offre {} effectuée après reprise du service IA", event.jobId());
            }
        });
    }

    int pendingCount() {
        return pending.size();
    }

    private boolean send(JobAnalysisRequested event) {
        try {
            aiClient.analyze(event.jobId(), event.title(), event.description());
            return true;
        } catch (RuntimeException e) {
            log.debug("Analyse IA de l'offre {} échouée : {}", event.jobId(), e.getMessage());
            return false;
        }
    }
}
