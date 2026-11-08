package com.smartrecruit.user.gdpr;

import com.smartrecruit.user.repository.DeletedCandidateRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-SEC-02 — with {@code gdpr.erasure.immediate=true}, the erasure is propagated right after the
 * deletion is committed, without waiting for the retry job.
 */
@SpringBootTest(properties = "gdpr.erasure.immediate=true")
@AutoConfigureMockMvc
class ImmediateErasureIntegrationTest {

    private static final HttpServer SERVICES;
    private static final AtomicInteger HITS = new AtomicInteger();

    static {
        try {
            SERVICES = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVICES.createContext("/", exchange -> {
            HITS.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        SERVICES.start();
    }

    @DynamicPropertySource
    static void pointToFakeServices(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + SERVICES.getAddress().getPort();
        registry.add("gdpr.erasure.auth-service-uri", () -> uri);
        registry.add("gdpr.erasure.ai-service-uri", () -> uri);
        registry.add("gdpr.erasure.job-service-uri", () -> uri);
        registry.add("gdpr.erasure.application-service-uri", () -> uri);
    }

    @AfterAll
    static void stop() {
        SERVICES.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private DeletedCandidateRepository deletedRepository;

    @Test
    void erasureStartsRightAfterTheDeletion() throws Exception {
        UUID candidateId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/candidates/me?confirm=true").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isNoContent());

        long deadline = System.currentTimeMillis() + 10_000;
        while (!deletedRepository.findById(candidateId).orElseThrow().isFullyErased()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(deletedRepository.findById(candidateId).orElseThrow().isFullyErased()).isTrue();
        assertThat(HITS.get()).isEqualTo(4);
    }
}
