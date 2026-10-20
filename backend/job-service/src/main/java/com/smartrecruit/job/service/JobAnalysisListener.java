package com.smartrecruit.job.service;

import com.smartrecruit.job.client.AiJobAnalysisClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Best-effort AI analysis of an offer, on its own thread and only after the commit: a slow or failing
 * ai-service never delays or breaks the recruiter's request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobAnalysisListener {

    private final AiJobAnalysisClient aiClient;

    @Async
    @TransactionalEventListener
    public void analyze(JobAnalysisRequested event) {
        try {
            aiClient.analyze(event.jobId(), event.title(), event.description());
        } catch (RuntimeException e) {
            log.warn("Analyse IA de l'offre {} indisponible, l'offre reste valide : {}", event.jobId(), e.getMessage());
        }
    }
}
