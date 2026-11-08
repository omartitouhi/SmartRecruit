package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.model.AuditLogEntry;
import com.smartrecruit.dashboard.repository.AuditLogRepository;
import com.smartrecruit.dashboard.security.InternalIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static com.smartrecruit.dashboard.support.SignedRequests.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-ADMIN-03 — GET /api/v1/dashboard/admin/audit-log. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditLogIntegrationTest {

    private static final String URL = "/api/v1/dashboard/admin/audit-log";
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private InternalIdentityVerifier verifier;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        save(ALICE, "CV_VIEWED", "2026-01-01T10:00:00Z");
        save(BOB, "CV_VIEWED", "2026-01-02T10:00:00Z");
        save(ALICE, "USER_STATUS_CHANGED", "2026-01-03T10:00:00Z");
        save(BOB, "JOB_MODERATED", "2026-01-04T10:00:00Z");
    }

    private void save(UUID actor, String action, String timestamp) {
        repository.save(AuditLogEntry.builder().actorId(actor).action(action).resourceType("RES")
                .resourceId(UUID.randomUUID()).timestamp(Instant.parse(timestamp)).build());
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(as(request, verifier, UUID.randomUUID(), "ADMIN"));
    }

    @Test
    void listsNewestFirstWithTheDocumentedShape() throws Exception {
        asAdmin(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].action").value("JOB_MODERATED"))
                .andExpect(jsonPath("$.content[3].action").value("CV_VIEWED"))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].actorId").value(BOB.toString()))
                .andExpect(jsonPath("$.content[0].resourceType").value("RES"))
                .andExpect(jsonPath("$.content[0].resourceId").exists())
                .andExpect(jsonPath("$.content[0].timestamp").value("2026-01-04T10:00:00Z"));
    }

    @Test
    void filtersByActorAndAction() throws Exception {
        asAdmin(get(URL).param("actorId", ALICE.toString()))
                .andExpect(jsonPath("$.totalElements").value(2));
        asAdmin(get(URL).param("action", "CV_VIEWED"))
                .andExpect(jsonPath("$.totalElements").value(2));
        asAdmin(get(URL).param("actorId", BOB.toString()).param("action", "CV_VIEWED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].timestamp").value("2026-01-02T10:00:00Z"));
        asAdmin(get(URL).param("action", "UNKNOWN")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByInclusiveDateRange() throws Exception {
        asAdmin(get(URL).param("from", "2026-01-02T10:00:00Z").param("to", "2026-01-03T10:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("USER_STATUS_CHANGED"));
        asAdmin(get(URL).param("from", "2026-01-03T12:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(1));
        asAdmin(get(URL).param("to", "2026-01-01T09:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginatesAndCapsThePageSize() throws Exception {
        asAdmin(get(URL).param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].action").value("CV_VIEWED"));
        asAdmin(get(URL).param("size", "5000")).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsMalformedParameters() throws Exception {
        asAdmin(get(URL).param("from", "yesterday")).andExpect(status().isBadRequest());
        asAdmin(get(URL).param("actorId", "not-a-uuid")).andExpect(status().isBadRequest());
        asAdmin(get(URL).param("from", "2026-02-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isReservedToAdministrators() throws Exception {
        for (String role : new String[]{"RECRUITER", "CANDIDATE"}) {
            mockMvc.perform(as(get(URL), verifier, UUID.randomUUID(), role)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
