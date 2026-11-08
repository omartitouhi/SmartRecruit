package com.smartrecruit.job.config;

import com.smartrecruit.job.service.JobExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs {@link JobExpiryService} at start-up and then every {@code jobs.expiry.interval} (default 5 min)
 * — EXT-JOB-01. Disabled with {@code jobs.expiry.enabled=false} (tests call the service directly).
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "jobs.expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JobExpiryScheduler {

    private final JobExpiryService expiryService;

    @Scheduled(initialDelayString = "${jobs.expiry.initial-delay:30s}", fixedDelayString = "${jobs.expiry.interval:5m}")
    public void closeExpiredOffers() {
        try {
            expiryService.closeExpiredOffers();
        } catch (DataAccessException e) {
            log.warn("Clôture automatique des offres échouée, nouvel essai au prochain passage : {}", e.getMessage());
        }
    }
}
