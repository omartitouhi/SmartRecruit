package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.model.AuditLogEntry;
import com.smartrecruit.dashboard.repository.AuditLogRepository;
import com.smartrecruit.dashboard.support.AbstractUpstreamIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-ADMIN-01 — user management through auth-service. */
class AdminUserIntegrationTest extends AbstractUpstreamIntegrationTest {

    private static final String USERS = "/api/v1/dashboard/admin/users";
    private static final String ACCOUNTS = "/internal/v1/accounts";

    @Autowired
    private AuditLogRepository auditLog;

    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditLog.deleteAll();
    }

    private String account(UUID id, String status) {
        return """
                {"id": "%s", "email": "bob@example.com", "role": "CANDIDATE", "status": "%s",
                 "createdAt": "2026-01-01T10:00:00Z", "updatedAt": "2026-01-02T10:00:00Z"}
                """.formatted(id, status);
    }

    @Test
    void listsUsersRelayingFiltersAndPagination() throws Exception {
        FAKE.respond("GET", ACCOUNTS, 200, """
                {"content": [%s], "page": 1, "size": 5, "totalElements": 6, "totalPages": 2}
                """.formatted(account(target, "ACTIVE")));

        asAdmin(get(USERS).param("role", "CANDIDATE").param("status", "ACTIVE").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("bob@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        var call = FAKE.callsTo("GET", ACCOUNTS).getFirst();
        assertThat(call.target()).contains("role=CANDIDATE", "status=ACTIVE", "page=1", "size=5");
        assertThat(call.role()).isEqualTo("SERVICE");
        assertThat(call.h2cUpgrade()).isFalse();
    }

    @Test
    void listOmitsFiltersThatAreNotSet() throws Exception {
        FAKE.respond("GET", ACCOUNTS, 200, "{\"content\": [], \"page\": 0, \"size\": 20, \"totalElements\": 0, \"totalPages\": 0}");

        asAdmin(get(USERS)).andExpect(status().isOk());

        assertThat(FAKE.callsTo("GET", ACCOUNTS).getFirst().target()).doesNotContain("role=", "status=");
    }

    @Test
    void invalidFilterRejectedByAuthServiceIsA400() throws Exception {
        FAKE.respond("GET", ACCOUNTS, 400, "{\"message\": \"bad\"}");

        asAdmin(get(USERS).param("role", "WIZARD")).andExpect(status().isBadRequest());
    }

    @Test
    void listIsA502WhenAuthServiceIsDown() throws Exception {
        FAKE.respond("GET", ACCOUNTS, 500, "boom");

        asAdmin(get(USERS)).andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void disablesAnotherUserAndWritesAnAuditEntry() throws Exception {
        UUID admin = UUID.randomUUID();
        FAKE.respond("PATCH", ACCOUNTS + "/" + target + "/status", 200, account(target, "DISABLED"));

        perform(patch(USERS + "/{id}/status", target).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"DISABLED\"}"), admin, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.id").value(target.toString()));

        assertThat(FAKE.callsTo("PATCH", ACCOUNTS + "/" + target + "/status").getFirst().body())
                .contains("\"status\":\"DISABLED\"");
        List<AuditLogEntry> entries = auditLog.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAction()).isEqualTo("USER_STATUS_CHANGED");
        assertThat(entries.getFirst().getActorId()).isEqualTo(admin);
        assertThat(entries.getFirst().getResourceId()).isEqualTo(target);
        assertThat(entries.getFirst().getResourceType()).isEqualTo("ACCOUNT");
    }

    @Test
    void anAdminCannotDisableThemself() throws Exception {
        perform(patch(USERS + "/{id}/status", target).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"DISABLED\"}"), target, "ADMIN")
                .andExpect(status().isBadRequest());

        assertThat(FAKE.calls()).isEmpty();
        assertThat(auditLog.count()).isZero();
    }

    @Test
    void reEnablingIsAllowedAndUnknownUsersAre404() throws Exception {
        FAKE.respond("PATCH", ACCOUNTS + "/" + target + "/status", 200, account(target, "ACTIVE"));

        perform(patch(USERS + "/{id}/status", target).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ACTIVE\"}"), target, "ADMIN")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        asAdmin(patch(USERS + "/{id}/status", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ACTIVE\"}")).andExpect(status().isNotFound());
        assertThat(auditLog.count()).isEqualTo(1);
    }

    @Test
    void statusIsValidatedBeforeAnyCall() throws Exception {
        for (String body : new String[]{"{}", "{\"status\": \"DELETED\"}", "{\"status\": \"WIZARD\"}"}) {
            asAdmin(patch(USERS + "/{id}/status", target).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.status").exists());
        }
        assertThat(FAKE.calls()).isEmpty();
    }

    @Test
    void statusChangeIsA502WhenAuthServiceIsDownAndNothingIsAudited() throws Exception {
        FAKE.respond("PATCH", ACCOUNTS + "/" + target + "/status", 503, "");

        asAdmin(patch(USERS + "/{id}/status", target).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"DISABLED\"}")).andExpect(status().isBadGateway());

        assertThat(auditLog.count()).isZero();
    }

    @Test
    void recruitersAndCandidatesAreDenied() throws Exception {
        for (String role : new String[]{"RECRUITER", "CANDIDATE"}) {
            perform(get(USERS), UUID.randomUUID(), role).andExpect(status().isForbidden());
            perform(patch(USERS + "/{id}/status", target).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\": \"DISABLED\"}"), UUID.randomUUID(), role).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        assertThat(FAKE.calls()).isEmpty();
    }
}
