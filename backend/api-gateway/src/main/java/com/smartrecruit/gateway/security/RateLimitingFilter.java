package com.smartrecruit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force / credential-stuffing protection (BE-SEC-05): at most {@code maxRequests} calls per
 * client IP and per sensitive path in a sliding window, otherwise 429 with {@code Retry-After}.
 * Runs before JWT validation so rejected calls cost nothing downstream.
 * <p>
 * State is in memory, which is enough for the single gateway instance of this project; several
 * instances would need a shared store (e.g. Redis).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxRequests;
    private final long windowMillis;
    private final Set<String> paths;
    private final boolean trustForwardedFor;
    private final Clock clock;
    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties properties, Clock clock) {
        this.maxRequests = properties.maxRequests();
        this.windowMillis = properties.window().toMillis();
        this.paths = Set.copyOf(properties.paths());
        this.trustForwardedFor = properties.trustForwardedFor();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod()) || !paths.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        long retryAfterMillis = tryAcquire(clientIp(request) + "|" + request.getRequestURI(), now);
        if (retryAfterMillis > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, (retryAfterMillis + 999) / 1000)));
            GatewayErrorWriter.write(response, request, HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives depuis cette adresse, réessayez plus tard");
            return;
        }
        chain.doFilter(request, response);
    }

    /** @return 0 if the request is allowed, otherwise the milliseconds until a slot frees up */
    private long tryAcquire(String key, long now) {
        if (requestLog.size() > CLEANUP_THRESHOLD) {
            requestLog.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        }
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return timestamps.peekFirst() + windowMillis - now;
            }
            timestamps.addLast(now);
            return 0;
        }
    }

    private boolean isExpired(Deque<Long> timestamps, long now) {
        synchronized (timestamps) {
            return timestamps.isEmpty() || timestamps.peekLast() <= now - windowMillis;
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
