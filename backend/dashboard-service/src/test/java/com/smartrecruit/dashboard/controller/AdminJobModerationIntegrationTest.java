package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.model.AuditLogEntry;
import com.smartrecruit.dashboard.repository.AuditLogRepository;
import com.smartrecruit.dashboard.support.AbstractUpstreamIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-ADMIN-02 — PATCH /api/v1/dashboard/admin/jobs/{id}/moderation proxied to job-service. */
class AdminJobModerationIntegrationTest extends AbstractUpstreamIntegrationTest {

    private static final String URL = "/api/v1/dashboard/admin/jobs/{id}/moderation";

    @Autowired
    private AuditLogRepository auditLog;

    private final UUID jobId = UUID.randomUUID();
    private final String upstreamPath = "/internal/v1/jobs/" + jobId + "/moderation";

    @BeforeEach
    void setUp() {
        auditLog.deleteAll();
    }

    private static String reason(String text) {
        return "{\"reason\": \"" + text + "\"}";
    }

    @Test
    void relaysTheReasonReturnsTheJobAndAuditsTheModeration() throws Exception {
        UUID admin = UUID.randomUUID();
        FAKE.respond("PATCH", upstreamPath, 200,
                "{\"id\": \"%s\", \"title\": \"Dev Java\", \"status\": \"MODERATED\", \"moderationReason\": \"Offre trompeuse\"}"
                        .formatted(jobId));

        perform(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON).content(reason("Offre trompeuse")),
                admin, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("MODERATED"))
                .andExpect(jsonPath("$.moderationReason").value("Offre trompeuse"));

        var call = FAKE.callsTo("PATCH", upstreamPath).getFirst();
        assertThat(call.body()).contains("\"reason\":\"Offre trompeuse\"");
        assertThat(call.role()).isEqualTo("SERVICE");
        assertThat(call.h2cUpgrade()).isFalse();
        AuditLogEntry entry = auditLog.findAll().getFirst();
        assertThat(entry.getAction()).isEqualTo("JOB_MODERATED");
        assertThat(entry.getActorId()).isEqualTo(admin);
        assertThat(entry.getResourceType()).isEqualTo("JOB");
        assertThat(entry.getResourceId()).isEqualTo(jobId);
    }

    @Test
    void reasonIsSanitizedBeforeBeingForwarded() throws Exception {
        FAKE.respond("PATCH", upstreamPath, 200, "{\"id\": \"%s\"}".formatted(jobId));

        asAdmin(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON)
                .content(reason("Spam <script>alert(1)</script>"))).andExpect(status().isOk());

        assertThat(FAKE.callsTo("PATCH", upstreamPath).getFirst().body()).contains("Spam").doesNotContain("script");
    }

    @Test
    void unknownJobIs404AndNothingIsAudited() throws Exception {
        FAKE.respond("PATCH", upstreamPath, 404, "{\"message\": \"Offre introuvable\"}");

        asAdmin(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Offre introuvable"));
        assertThat(auditLog.count()).isZero();
    }

    @Test
    void jobServiceFailureIs502() throws Exception {
        FAKE.respond("PATCH", upstreamPath, 500, "boom");

        asAdmin(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                .andExpect(status().isBadGateway());
        assertThat(auditLog.count()).isZero();
    }

    @Test
    void reasonIsRequiredAndLimitedTo500Characters() throws Exception {
        for (String body : new String[]{"{}", reason("  "), reason("a".repeat(501))}) {
            asAdmin(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.reason").exists());
        }
        assertThat(FAKE.calls()).isEmpty();
        FAKE.respond("PATCH", upstreamPath, 200, "{\"id\": \"%s\"}".formatted(jobId));
        asAdmin(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON).content(reason("a".repeat(500))))
                .andExpect(status().isOk());
    }

    @Test
    void recruitersAndCandidatesAreDenied() throws Exception {
        for (String role : new String[]{"RECRUITER", "CANDIDATE"}) {
            perform(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON).content(reason("x")),
                    UUID.randomUUID(), role).andExpect(status().isForbidden());
        }
        mockMvc.perform(patch(URL, jobId).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                .andExpect(status().isUnauthorized());
        assertThat(FAKE.calls()).isEmpty();
    }
}
