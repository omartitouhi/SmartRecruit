package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.CvAnalysisStatus;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.smartrecruit.user.controller.CandidateProfileCreateIntegrationTest.FULL_PROFILE;
import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-CV-01 — the AI analysis is triggered asynchronously after the upload and never breaks it. */
@SpringBootTest(properties = "cv-analysis.enabled=true")
@AutoConfigureMockMvc
class CvAnalysisIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final String PARSE_PATH = "/api/v1/ai/cv-parsing/parse";

    /** Behaviour of the fake ai-service for the current test; receives the call number (1-based). */
    private interface Behaviour {
        int handle(int callNumber) throws InterruptedException;
    }

    private static final HttpServer AI;
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    /** Requests carrying h2c upgrade headers: uvicorn (the real ai-service) cannot handle them with a body. */
    private static final List<String> H2C_UPGRADES = new CopyOnWriteArrayList<>();
    private static volatile Behaviour behaviour = call -> 200;

    static {
        try {
            AI = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        AI.createContext(PARSE_PATH, CvAnalysisIntegrationTest::handle);
        AI.start();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (exchange.getRequestHeaders().containsKey("Upgrade")
                    || exchange.getRequestHeaders().containsKey("HTTP2-Settings")) {
                H2C_UPGRADES.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders());
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            BODIES.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Content-Type")
                    + " " + exchange.getRequestHeaders().getFirst(InternalIdentityVerifier.USER_ROLE) + "\n" + body);
            int code = behaviour.handle(BODIES.size());
            byte[] json = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, code == 200 ? json.length : -1);
            if (code == 200) {
                exchange.getResponseBody().write(json);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @DynamicPropertySource
    static void pointToFakeAi(DynamicPropertyRegistry registry) {
        registry.add("gdpr.erasure.ai-service-uri", () -> "http://localhost:" + AI.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        AI.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CandidateProfileRepository repository;

    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        BODIES.clear();
        H2C_UPGRADES.clear();
        behaviour = call -> 200;
        mockMvc.perform(put("/api/v1/users/candidates/me").with(as(verifier, candidateId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_PROFILE))
                .andExpect(status().isCreated());
    }

    private void upload(byte[] content) throws Exception {
        mockMvc.perform(multipart("/api/v1/users/candidates/me/cv")
                        .file(new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, content))
                        .with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvParsedAt").doesNotExist())
                .andExpect(jsonPath("$.cvAnalysisStatus").value("PENDING"));
    }

    private boolean parsed() {
        return repository.findById(candidateId).orElseThrow().getCvParsedAt() != null;
    }

    private CvAnalysisStatus storedStatus() {
        return repository.findById(candidateId).orElseThrow().analysisStatus();
    }

    private String statusInApi() throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/candidates/me").with(as(verifier, candidateId, "CANDIDATE")))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.cvAnalysisStatus");
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        return condition.getAsBoolean();
    }

    @Test
    void aProfileWithoutCvHasNoAnalysisStatus() throws Exception {
        assertThat(statusInApi()).isEqualTo("NONE");
    }

    @Test
    void uploadTriggersTheAiAndMarksTheCvAsParsed() throws Exception {
        upload(PDF);

        assertThat(await(this::parsed)).isTrue();
        assertThat(storedStatus()).isEqualTo(CvAnalysisStatus.DONE);
        assertThat(statusInApi()).isEqualTo("DONE");
        assertThat(BODIES).hasSize(1);
        assertThat(H2C_UPGRADES).as("HTTP/1.1 only: no h2c upgrade towards uvicorn").isEmpty();
        String request = BODIES.get(0);
        assertThat(request).startsWith("POST multipart/form-data;boundary=");
        assertThat(request).contains(" SERVICE\n");
        assertThat(request).contains("name=\"file\"; filename=\"cv.pdf\"", "Content-Type: application/pdf", "%PDF-");
        assertThat(request).containsPattern("name=\"candidateId\"[\\s\\S]*\\r\\n\\r\\n" + candidateId);
    }

    @Test
    void aiFailureLeavesTheUploadValidAndTheCvUnparsed() throws Exception {
        behaviour = call -> 500;

        upload(PDF);

        assertThat(await(() -> !BODIES.isEmpty())).isTrue();
        assertThat(await(() -> storedStatus() == CvAnalysisStatus.FAILED)).isTrue();
        assertThat(statusInApi()).isEqualTo("FAILED");
        assertThat(parsed()).isFalse();
        assertThat(repository.findById(candidateId).orElseThrow().getCvStorageKey()).isNotBlank();
    }

    @Test
    void analysisOfAReplacedCvDoesNotMarkTheNewOneAsParsed() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        behaviour = call -> {
            if (call == 1) {
                release.await(10, TimeUnit.SECONDS);
                return 200;
            }
            return 500; // the new CV's own analysis fails, so only a stale update could set cvParsedAt
        };

        upload(PDF);
        assertThat(await(() -> BODIES.size() == 1)).isTrue();
        upload("%PDF-1.4 second version".getBytes(StandardCharsets.US_ASCII));
        assertThat(await(() -> BODIES.size() == 2)).isTrue();

        release.countDown();
        assertThat(await(() -> storedStatus() == CvAnalysisStatus.FAILED)).isTrue();
        Thread.sleep(500);
        assertThat(parsed()).isFalse();
        assertThat(storedStatus()).isEqualTo(CvAnalysisStatus.FAILED);
    }

    @Test
    void failureOfAReplacedCvDoesNotOverrideTheResultOfTheNewOne() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        behaviour = call -> {
            if (call == 1) {
                release.await(10, TimeUnit.SECONDS);
                return 500; // the stale analysis fails late
            }
            return 200;
        };

        upload(PDF);
        assertThat(await(() -> BODIES.size() == 1)).isTrue();
        upload("%PDF-1.4 second version".getBytes(StandardCharsets.US_ASCII));
        assertThat(await(this::parsed)).isTrue();

        release.countDown();
        Thread.sleep(500);
        assertThat(storedStatus()).isEqualTo(CvAnalysisStatus.DONE);
    }

    @Test
    void aNewUploadPutsTheStatusBackToPending() throws Exception {
        upload(PDF);
        assertThat(await(() -> storedStatus() == CvAnalysisStatus.DONE)).isTrue();
        behaviour = call -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 200;
        };

        upload("%PDF-1.4 second version".getBytes(StandardCharsets.US_ASCII));

        assertThat(statusInApi()).isEqualTo("PENDING");
    }
}
