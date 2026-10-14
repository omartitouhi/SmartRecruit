package com.smartrecruit.user.gdpr;

import com.smartrecruit.user.model.DeletedCandidate;
import com.smartrecruit.user.repository.DeletedCandidateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Propagates account deletions to every service holding the candidate's personal data
 * (BE-SEC-02): auth-service (account) and ai-service (analysis results). user-service's own data
 * is already gone (BE-USER-04). Each step is retried until it succeeds; a deletion still
 * incomplete after its 30-day deadline is logged as an error so it can be handled manually.
 */
@Slf4j
@Component
public class PersonalDataErasureProcessor {

    private final DeletedCandidateRepository repository;
    private final ErasureClient client;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PersonalDataErasureProcessor(DeletedCandidateRepository repository, ErasureClient client,
                                        TransactionTemplate transactionTemplate, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** @return number of deletions still incomplete after this run */
    public int processPending() {
        int remaining = 0;
        for (UUID id : repository.findPendingErasureIds()) {
            if (!process(id)) {
                remaining++;
            }
        }
        return remaining;
    }

    /** @return true once the deletion is propagated everywhere */
    public boolean process(UUID candidateId) {
        try {
            Boolean done = transactionTemplate.execute(status -> repository.findById(candidateId)
                    .map(this::attempt)
                    .orElse(true));
            return Boolean.TRUE.equals(done);
        } catch (ObjectOptimisticLockingFailureException e) {
            // processed concurrently (immediate trigger and scheduled job): the next run will catch up
            return false;
        }
    }

    private boolean attempt(DeletedCandidate deletion) {
        Instant now = clock.instant();
        StringBuilder errors = new StringBuilder();

        if (deletion.getAuthErasedAt() == null) {
            try {
                client.eraseAccount(deletion.getId());
                deletion.setAuthErasedAt(clock.instant());
            } catch (RestClientException e) {
                errors.append("auth-service: ").append(e.getMessage()).append("; ");
            }
        }
        if (deletion.getAiErasedAt() == null) {
            try {
                client.eraseAiData(deletion.getId());
                deletion.setAiErasedAt(clock.instant());
            } catch (RestClientException e) {
                errors.append("ai-service: ").append(e.getMessage()).append("; ");
            }
        }

        deletion.setErasureAttempts(deletion.getErasureAttempts() + 1);
        deletion.setLastErasureAttemptAt(now);
        deletion.setLastErasureError(errors.isEmpty() ? null : truncate(errors.toString()));

        if (deletion.isFullyErased()) {
            log.info("Droit à l'oubli : données du candidat {} effacées dans tous les services", deletion.getId());
        } else if (deletion.getEraseDeadline() != null && now.isAfter(deletion.getEraseDeadline())) {
            log.error("RGPD : effacement du candidat {} toujours incomplet après la date limite {} ({} tentatives) : {}",
                    deletion.getId(), deletion.getEraseDeadline(), deletion.getErasureAttempts(), errors);
        } else {
            log.warn("RGPD : effacement du candidat {} incomplet, nouvel essai planifié : {}", deletion.getId(), errors);
        }
        return deletion.isFullyErased();
    }

    private static String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 497) + "...";
    }
}
