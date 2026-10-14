package com.smartrecruit.user.gdpr;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When the erasure is propagated (BE-SEC-02): right after the deletion is committed, then
 * periodically until every service confirmed.
 */
public final class ErasureTriggers {

    private ErasureTriggers() {
    }

    /** Published by user-service when a candidate deletes their account. */
    public record AccountDeletedEvent(java.util.UUID accountId) {
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "gdpr.erasure", name = "immediate", havingValue = "true")
    static class Immediate {

        private final PersonalDataErasureProcessor processor;

        /** Asynchronous: the candidate's DELETE request does not wait for the other services. */
        @Async
        @TransactionalEventListener
        public void onAccountDeleted(AccountDeletedEvent event) {
            processor.process(event.accountId());
        }
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "gdpr.erasure", name = "scheduler-enabled", havingValue = "true")
    static class Retry {

        private final PersonalDataErasureProcessor processor;

        @Scheduled(fixedDelayString = "${gdpr.erasure.retry-interval}", initialDelayString = "${gdpr.erasure.retry-interval}")
        public void retryPending() {
            processor.processPending();
        }
    }

    @Configuration
    @EnableAsync
    @EnableScheduling
    static class Enable {
    }
}
