package com.smartrecruit.user.gdpr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propagation of account deletions to the other services (BE-SEC-02).
 *
 * @param deadline         legal limit to erase the data everywhere (30 days)
 * @param retryInterval    delay between two attempts for deletions not yet fully propagated
 * @param schedulerEnabled run the retry job (disabled in tests)
 * @param immediate        also try right after the deletion is committed, without waiting for the job
 * @param authServiceUri   base URI of auth-service
 * @param aiServiceUri     base URI of ai-service
 * @param jobServiceUri    base URI of job-service (saved offers, EXT-SAVED-01)
 * @param applicationServiceUri base URI of application-service (cover letters and notes, EXT-APP-01/03)
 */
@ConfigurationProperties(prefix = "gdpr.erasure")
public record GdprErasureProperties(Duration deadline, Duration retryInterval, boolean schedulerEnabled,
                                    boolean immediate, String authServiceUri, String aiServiceUri,
                                    String jobServiceUri, String applicationServiceUri) {
}
