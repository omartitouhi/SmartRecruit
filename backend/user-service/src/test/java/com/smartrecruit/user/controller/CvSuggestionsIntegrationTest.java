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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-CV-02 — GET /api/v1/users/candidates/me/cv-suggestions against a fake ai-service. */
@SpringBootTest
@AutoConfigureMockMvc
class CvSuggestionsIntegrationTest {

    private static final String PARSED = """
            {"skills": ["Java", "Spring Boot"],
             "experiences": [
               {"title": "Backend Developer", "company": "Acme", "startDate": "2022-01-15", "endDate": null,
                "description": "APIs REST"},
               {"title": "Intern", "company": "Globex", "startDate": "2020-06-01", "endDate": "2021-12-31",
                "description": "Tests"}],
             "education": [{"degree": "Master", "institution": "ESPRIT", "year": 2025}],
             "languages": ["Français", "Anglais"]}
            """;

    private static final HttpServer AI;
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile int code = 200;
    private static volatile String body = PARSED;

    static {
        try {
            AI = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        AI.createContext("/api/v1/ai/cv-parsing/candidates/", CvSuggestionsIntegrationTest::handle);
        AI.start();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            boolean h2c = exchange.getRequestHeaders().containsKey("Upgrade");
            REQUESTS.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + exchange.getRequestHeaders().getFirst(InternalIdentityVerifier.USER_ROLE) + (h2c ? " h2c" : ""));
            byte[] json = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, code == 200 ? json.length : -1);
            if (code == 200) {
                exchange.getResponseBody().write(json);
            }
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

    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        REQUESTS.clear();
        code = 200;
        body = PARSED;
    }

    @Test
    void returnsTheAiResultWithDatesAsYearMonth() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/me/cv-suggestions").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.experiences.length()").value(2))
                .andExpect(jsonPath("$.experiences[0].title").value("Backend Developer"))
                .andExpect(jsonPath("$.experiences[0].company").value("Acme"))
                .andExpect(jsonPath("$.experiences[0].startDate").value("2022-01"))
                .andExpect(jsonPath("$.experiences[0].endDate").doesNotExist())
                .andExpect(jsonPath("$.experiences[0].description").value("APIs REST"))
                .andExpect(jsonPath("$.experiences[1].startDate").value("2020-06"))
                .andExpect(jsonPath("$.experiences[1].endDate").value("2021-12"))
                .andExpect(jsonPath("$.education[0].degree").value("Master"))
                .andExpect(jsonPath("$.education[0].institution").value("ESPRIT"))
                .andExpect(jsonPath("$.education[0].year").value(2025))
                .andExpect(jsonPath("$.languages[1]").value("Anglais"));

        assertThat(REQUESTS).containsExactly("GET /api/v1/ai/cv-parsing/candidates/" + candidateId + " SERVICE");
    }

    @Test
    void noAnalysedCvIs404() throws Exception {
        code = 404;

        mockMvc.perform(get("/api/v1/users/candidates/me/cv-suggestions").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFailingAiIs503() throws Exception {
        code = 500;

        mockMvc.perform(get("/api/v1/users/candidates/me/cv-suggestions").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void aMalformedAiAnswerIs503() throws Exception {
        body = "this is not json";

        mockMvc.perform(get("/api/v1/users/candidates/me/cv-suggestions").with(as(verifier, candidateId, "CANDIDATE")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void onlyCandidatesCanReadSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/users/candidates/me/cv-suggestions").with(as(verifier, candidateId, "RECRUITER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/candidates/me/cv-suggestions").with(as(verifier, candidateId, "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/candidates/me/cv-suggestions")).andExpect(status().isUnauthorized());
        assertThat(REQUESTS).isEmpty();
    }
}
