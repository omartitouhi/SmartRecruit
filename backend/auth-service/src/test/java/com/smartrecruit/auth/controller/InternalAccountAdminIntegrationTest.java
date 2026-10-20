package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.PasswordResetTokenRepository;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import com.smartrecruit.auth.security.InternalIdentityVerifier;
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

import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-ADMIN-01 — internal account listing, status change and stats called by dashboard-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalAccountAdminIntegrationTest {

    private static final String PASSWORD = "Secret123!";
    private static final UUID SERVICE_ID = new UUID(0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private InternalIdentityVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        register(mockMvc, "alice@example.com", PASSWORD, "CANDIDATE");
        register(mockMvc, "bob@example.com", PASSWORD, "CANDIDATE");
        register(mockMvc, "carol@example.com", PASSWORD, "RECRUITER");
        Account bob = accountRepository.findByEmail("bob@example.com").orElseThrow();
        bob.setStatus(AccountStatus.DISABLED);
        accountRepository.save(bob);
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String role) {
        long now = Instant.now().getEpochSecond();
        return request
                .header(InternalIdentityVerifier.USER_ID, SERVICE_ID.toString())
                .header(InternalIdentityVerifier.USER_ROLE, role)
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                .header(InternalIdentityVerifier.SIGNATURE, verifier.sign(SERVICE_ID.toString(), role, now));
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(signed(request, "SERVICE"));
    }

    @Test
    void listsAllAccountsPaginatedWithoutExposingPasswords() throws Exception {
        call(get("/internal/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void filtersByRoleAndStatus() throws Exception {
        call(get("/internal/v1/accounts").param("role", "CANDIDATE"))
                .andExpect(jsonPath("$.totalElements").value(2));
        call(get("/internal/v1/accounts").param("status", "DISABLED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("bob@example.com"));
        call(get("/internal/v1/accounts").param("role", "CANDIDATE").param("status", "ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
        call(get("/internal/v1/accounts").param("role", "ADMIN"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginatesAndCapsThePageSize() throws Exception {
        call(get("/internal/v1/accounts").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        call(get("/internal/v1/accounts").param("size", "10000"))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void invalidFilterValueIsRejected() throws Exception {
        call(get("/internal/v1/accounts").param("role", "WIZARD")).andExpect(status().isBadRequest());
    }

    @Test
    void listingIsReservedToBackendServices() throws Exception {
        mockMvc.perform(signed(get("/internal/v1/accounts"), "ADMIN")).andExpect(status().isForbidden());
        mockMvc.perform(signed(get("/internal/v1/accounts"), "CANDIDATE")).andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/v1/accounts")).andExpect(status().isUnauthorized());
    }
}
