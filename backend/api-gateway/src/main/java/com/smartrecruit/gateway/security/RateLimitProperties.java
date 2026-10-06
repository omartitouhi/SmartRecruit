package com.smartrecruit.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Rate limiting of the sensitive endpoints (BE-SEC-05).
 *
 * @param maxRequests       requests allowed per client IP and per path inside {@code window}
 * @param window            sliding window length
 * @param paths             exact paths subject to the limit
 * @param trustForwardedFor use the first {@code X-Forwarded-For} address as client IP; enable only
 *                          behind a reverse proxy that overwrites this header
 */
@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(int maxRequests, Duration window, List<String> paths, boolean trustForwardedFor) {
}
