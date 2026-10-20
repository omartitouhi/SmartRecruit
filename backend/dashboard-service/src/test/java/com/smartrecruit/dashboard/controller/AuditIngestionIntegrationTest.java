package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.model.AuditLogEntry;
import com.smartrecruit.dashboard.repository.AuditLogRepository;
import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static com.smartrecruit.dashboard.support.SignedRequests.as;
import static com.smartrecruit.dashboard.support.SignedRequests.asService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-ADMIN-03 — POST /internal/v1/audit-events. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditIngestionIntegrationTest {

    private static final String URL = "/internal/v1/audit-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private InternalIdentityVerifier verifier;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(asService(post(URL), verifier).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(UUID actor, String action, String type, UUID resource) {
        return """
                {"actorId": %s, "action": %s, "resourceType": %s, "resourceId": %s}
                """.formatted(quote(actor), quote(action), quote(type), quote(resource));
    }

    private static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void storesTheEventWithAServerSideTimestamp() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        Instant before = Instant.now();

        send(body(actor, "CV_VIEWED", "CANDIDATE_CV", resource)).andExpect(status().isCreated());

        AuditLogEntry entry = repository.findAll().getFirst();
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getActorId()).isEqualTo(actor);
        assertThat(entry.getAction()).isEqualTo("CV_VIEWED");
        assertThat(entry.getResourceType()).isEqualTo("CANDIDATE_CV");
        assertThat(entry.getResourceId()).isEqualTo(resource);
        assertThat(entry.getTimestamp()).isBetween(before, Instant.now());
    }

    @Test
    void clientSuppliedTimestampIsIgnored() throws Exception {
        send("""
                {"actorId": "%s", "action": "CV_VIEWED", "resourceType": "CV", "resourceId": "%s",
                 "timestamp": "2001-01-01T00:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isCreated());

        assertThat(repository.findAll().getFirst().getTimestamp()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void rejectsMissingOrInvalidFields() throws Exception {
        UUID id = UUID.randomUUID();
        send(body(null, "CV_VIEWED", "CV", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.actorId").exists());
        send(body(id, null, "CV", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.action").exists());
        send(body(id, "cv viewed", "CV", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.action").exists());
        send(body(id, "A".repeat(65), "CV", id)).andExpect(status().isBadRequest());
        send(body(id, "CV_VIEWED", " ", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.resourceType").exists());
        send(body(id, "CV_VIEWED", "T".repeat(65), id)).andExpect(status().isBadRequest());
        send(body(id, "CV_VIEWED", "CV", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.resourceId").exists());
        send("{\"actorId\": \"nope\", \"action\": \"CV_VIEWED\", \"resourceType\": \"CV\", \"resourceId\": \""
                + id + "\"}").andExpect(status().isBadRequest());

        assertThat(repository.count()).isZero();
    }

    @Test
    void maximumLengthsAreAccepted() throws Exception {
        UUID id = UUID.randomUUID();
        send(body(id, "A".repeat(64), "T".repeat(64), id)).andExpect(status().isCreated());
    }

    @Test
    void onlyBackendServicesCanPostEvents() throws Exception {
        UUID id = UUID.randomUUID();
        for (String role : new String[]{"ADMIN", "RECRUITER", "CANDIDATE"}) {
            mockMvc.perform(as(post(URL), verifier, id, role).contentType(MediaType.APPLICATION_JSON)
                    .content(body(id, "CV_VIEWED", "CV", id))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(id, "CV_VIEWED", "CV", id)))
                .andExpect(status().isUnauthorized());

        assertThat(repository.count()).isZero();
    }
}
