package com.smartrecruit.user.controller;

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
import java.util.function.BooleanSupplier;

import static com.smartrecruit.user.controller.CandidateProfileCreateIntegrationTest.FULL_PROFILE;
import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit of recruiter and admin views of candidate data, towards a fake dashboard-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CandidateAuditIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private static final HttpServer DASHBOARD;
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    private static volatile int code = 201;

    static {
        try {
            DASHBOARD = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        DASHBOARD.createContext("/internal/v1/audit-events", CandidateAuditIntegrationTest::handle);
        DASHBOARD.start();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            EVENTS.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst(InternalIdentityVerifier.USER_ROLE)
                    + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(code, -1);
        }
    }

    @DynamicPropertySource
    static void pointToFakeDashboard(DynamicPropertyRegistry registry) {
        registry.add("services.dashboard-service-uri", () -> "http://localhost:" + DASHBOARD.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        DASHBOARD.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    private final UUID candidateId = UUID.randomUUID();
    private final UUID recruiterId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        EVENTS.clear();
        code = 201;
        mockMvc.perform(put("/api/v1/users/candidates/me").with(as(verifier, candidateId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_PROFILE))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/users/candidates/me/cv")
                        .file(new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk());
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        return condition.getAsBoolean();
    }

    private String json(String action, UUID actor) {
        return "\"actorId\":\"" + actor + "\",\"action\":\"" + action + "\",\"resourceType\":\"CANDIDATE\",\"resourceId\":\""
                + candidateId + "\"";
    }

    @Test
    void aRecruiterReadingAProfileIsAudited() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId).with(as(verifier, recruiterId, "RECRUITER")))
                .andExpect(status().isOk());

        assertThat(await(() -> EVENTS.size() == 1)).isTrue();
        assertThat(EVENTS.get(0)).startsWith("POST SERVICE ").contains(json("PROFILE_VIEWED", recruiterId));
    }

    @Test
    void anAdminDownloadingACvIsAudited() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId + "/cv").with(as(verifier, adminId, "ADMIN")))
                .andExpect(status().isOk());

        assertThat(await(() -> EVENTS.size() == 1)).isTrue();
        assertThat(EVENTS.get(0)).contains(json("CV_VIEWED", adminId));
    }

    @Test
    void aRecruiterDownloadingACvIsAudited() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId + "/cv").with(as(verifier, recruiterId, "RECRUITER")))
                .andExpect(status().isOk());

        assertThat(await(() -> EVENTS.size() == 1)).isTrue();
        assertThat(EVENTS.get(0)).contains(json("CV_VIEWED", recruiterId));
    }

    @Test
    void theCandidateReadingTheirOwnDataIsNotAudited() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/me").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId + "/cv").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk());

        Thread.sleep(500);
        assertThat(EVENTS).isEmpty();
    }

    @Test
    void failedOrDeniedReadsAreNotAudited() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/" + UUID.randomUUID()).with(as(verifier, recruiterId, "RECRUITER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/candidates/" + UUID.randomUUID() + "/cv").with(as(verifier, recruiterId, "RECRUITER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId).with(as(verifier, UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId + "/cv").with(as(verifier, UUID.randomUUID(), "CANDIDATE")))
                .andExpect(status().isForbidden());

        Thread.sleep(500);
        assertThat(EVENTS).isEmpty();
    }

    @Test
    void aFailingDashboardNeverAffectsTheResponse() throws Exception {
        code = 500;

        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId).with(as(verifier, recruiterId, "RECRUITER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/candidates/" + candidateId + "/cv").with(as(verifier, recruiterId, "RECRUITER")))
                .andExpect(status().isOk());

        assertThat(await(() -> EVENTS.size() == 2)).isTrue();
    }
}
