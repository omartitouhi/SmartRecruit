package com.smartrecruit.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private final MovableClock clock = new MovableClock();
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = filter(false);
    }

    private RateLimitingFilter filter(boolean trustForwardedFor) {
        return new RateLimitingFilter(new RateLimitProperties(3, Duration.ofMinutes(5),
                List.of(LOGIN, "/api/v1/auth/register"), trustForwardedFor), clock);
    }

    private MockHttpServletResponse call(RateLimitingFilter target, String path, String ip, String forwardedFor)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        target.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse call(String path, String ip) throws Exception {
        return call(filter, path, ip, null);
    }

    @Test
    void requestsAboveTheLimitGet429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call(LOGIN, "10.0.0.1").getStatus()).isEqualTo(200);
            clock.advance(Duration.ofSeconds(30));
        }

        MockHttpServletResponse blocked = call(LOGIN, "10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("\"status\":429");
        // the first request was 90 s ago: a slot frees up in 5 min - 90 s
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("210");
    }

    @Test
    void slotsAreFreedOnceTheWindowSlides() throws Exception {
        for (int i = 0; i < 3; i++) {
            call(LOGIN, "10.0.0.1");
        }
        assertThat(call(LOGIN, "10.0.0.1").getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(5));

        assertThat(call(LOGIN, "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void rejectedRequestsAreNotCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            call(LOGIN, "10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        for (int i = 0; i < 3; i++) {
            assertThat(call(LOGIN, "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void limitIsPerIpAndPerPath() throws Exception {
        for (int i = 0; i < 3; i++) {
            call(LOGIN, "10.0.0.1");
        }

        assertThat(call(LOGIN, "10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(call(LOGIN, "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(call("/api/v1/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void otherPathsAreNeverLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(call("/api/v1/jobs", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void forwardedForIsIgnoredUnlessTrusted() throws Exception {
        for (int i = 0; i < 3; i++) {
            call(filter, LOGIN, "10.0.0.1", "203.0.113." + i);
        }
        assertThat(call(filter, LOGIN, "10.0.0.1", "203.0.113.99").getStatus()).isEqualTo(429);

        RateLimitingFilter behindProxy = filter(true);
        for (int i = 0; i < 3; i++) {
            call(behindProxy, LOGIN, "10.0.0.1", "203.0.113.7, 10.0.0.1");
        }
        assertThat(call(behindProxy, LOGIN, "10.0.0.1", "203.0.113.7").getStatus()).isEqualTo(429);
        assertThat(call(behindProxy, LOGIN, "10.0.0.1", "203.0.113.8").getStatus()).isEqualTo(200);
    }

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-17T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
