package com.smartrecruit.user.gdpr;

import com.smartrecruit.user.model.DeletedCandidate;
import com.smartrecruit.user.repository.DeletedCandidateRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-SEC-02 — a deleted account is erased from every service holding the candidate's data.
 * A JDK HTTP server plays auth-service and ai-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonalDataErasureIntegrationTest {

    record Call(String method, String path, String role, String userId, String timestamp, String signature,
                boolean h2cUpgrade) {
    }

    private static final HttpServer SERVICES;
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final Map<String, Integer> STATUS_BY_PREFIX = new ConcurrentHashMap<>();

    static {
        try {
            SERVICES = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVICES.createContext("/", exchange -> {
            var headers = exchange.getRequestHeaders();
            String path = exchange.getRequestURI().getPath();
            CALLS.add(new Call(exchange.getRequestMethod(), path, headers.getFirst("X-Auth-User-Role"),
                    headers.getFirst("X-Auth-User-Id"), headers.getFirst("X-Auth-Timestamp"),
                    headers.getFirst("X-Auth-Signature"),
                    headers.containsKey("Upgrade") || headers.containsKey("HTTP2-Settings")));
            int status = STATUS_BY_PREFIX.entrySet().stream()
                    .filter(entry -> path.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(204);
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        SERVICES.start();
    }

    @DynamicPropertySource
    static void pointToFakeServices(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + SERVICES.getAddress().getPort();
        registry.add("gdpr.erasure.auth-service-uri", () -> uri);
        registry.add("gdpr.erasure.ai-service-uri", () -> uri);
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

    @Autowired
    private PersonalDataErasureProcessor processor;

    private static final String PROFILE = """
            {"firstName": "Alice", "lastName": "Martin", "email": "alice@example.com"}
            """;

    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        CALLS.clear();
        STATUS_BY_PREFIX.clear();
        deletedRepository.deleteAll();
        mockMvc.perform(put("/api/v1/users/candidates/me").with(as(verifier, candidateId, "CANDIDATE"))
                .contentType(MediaType.APPLICATION_JSON).content(PROFILE)).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/users/candidates/me?confirm=true").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletionGetsA30DayDeadline() {
        DeletedCandidate deletion = deletedRepository.findById(candidateId).orElseThrow();

        assertThat(Duration.between(deletion.getDeletedAt(), deletion.getEraseDeadline())).isEqualTo(Duration.ofDays(30));
        assertThat(deletion.isFullyErased()).isFalse();
    }

    @Test
    void erasureIsPropagatedToAuthAndAiServicesWithSignedServiceIdentity() {
        assertThat(processor.processPending()).isZero();

        assertThat(CALLS).extracting(Call::method, Call::path).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("DELETE", "/internal/v1/accounts/" + candidateId),
                org.assertj.core.groups.Tuple.tuple("DELETE", "/api/v1/ai/candidates/" + candidateId + "/data"));
        for (Call call : CALLS) {
            assertThat(call.h2cUpgrade()).as("HTTP/1.1 only: no h2c upgrade towards uvicorn").isFalse();
            assertThat(call.role()).isEqualTo("SERVICE");
            assertThat(call.signature())
                    .isEqualTo(verifier.sign(call.userId(), "SERVICE", Long.parseLong(call.timestamp())));
        }
        DeletedCandidate deletion = deletedRepository.findById(candidateId).orElseThrow();
        assertThat(deletion.isFullyErased()).isTrue();
        assertThat(deletion.getLastErasureError()).isNull();
    }

    @Test
    void failedStepIsRetriedUntilItSucceedsWithoutRepeatingTheOthers() {
        STATUS_BY_PREFIX.put("/api/v1/ai/", 503);

        assertThat(processor.processPending()).isEqualTo(1);
        DeletedCandidate afterFailure = deletedRepository.findById(candidateId).orElseThrow();
        assertThat(afterFailure.getAuthErasedAt()).isNotNull();
        assertThat(afterFailure.getAiErasedAt()).isNull();
        assertThat(afterFailure.getErasureAttempts()).isEqualTo(1);
        assertThat(afterFailure.getLastErasureError()).contains("ai-service");

        STATUS_BY_PREFIX.clear();
        CALLS.clear();
        assertThat(processor.processPending()).isZero();

        assertThat(CALLS).extracting(Call::path).containsExactly("/api/v1/ai/candidates/" + candidateId + "/data");
        assertThat(deletedRepository.findById(candidateId).orElseThrow().isFullyErased()).isTrue();
        CALLS.clear();
        processor.processPending();
        assertThat(CALLS).isEmpty();
    }

    @Test
    void unreachableServiceKeepsTheDeletionPending() {
        STATUS_BY_PREFIX.put("/internal/", 500);
        STATUS_BY_PREFIX.put("/api/v1/ai/", 500);

        processor.processPending();
        processor.processPending();

        DeletedCandidate deletion = deletedRepository.findById(candidateId).orElseThrow();
        assertThat(deletion.isFullyErased()).isFalse();
        assertThat(deletion.getErasureAttempts()).isEqualTo(2);
        assertThat(deletedRepository.findPendingErasureIds()).containsExactly(candidateId);
    }

    @Test
    void overdueDeletionIsStillProcessed() {
        DeletedCandidate deletion = deletedRepository.findById(candidateId).orElseThrow();
        deletion.setEraseDeadline(Instant.now().minus(Duration.ofDays(1)));
        deletedRepository.save(deletion);
        STATUS_BY_PREFIX.put("/api/v1/ai/", 500);

        assertThat(processor.process(candidateId)).isFalse();

        STATUS_BY_PREFIX.clear();
        assertThat(processor.process(candidateId)).isTrue();
    }
}
