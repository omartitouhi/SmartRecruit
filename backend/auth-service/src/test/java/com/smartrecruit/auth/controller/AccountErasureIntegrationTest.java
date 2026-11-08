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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static com.smartrecruit.auth.support.AuthTestHelper.json;
import static com.smartrecruit.auth.support.AuthTestHelper.login;
import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-SEC-02 — DELETE /internal/v1/accounts/{id}, called by user-service after an account deletion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountErasureIntegrationTest {

    private static final String EMAIL = "alice@example.com";
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

    private UUID accountId;

    @BeforeEach
    void setUp() throws Exception {
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        register(mockMvc, EMAIL, PASSWORD, "CANDIDATE");
        accountId = accountRepository.findByEmail(EMAIL).orElseThrow().getId();
    }

    private ResultActions erase(UUID target, String role) throws Exception {
        long now = Instant.now().getEpochSecond();
        return mockMvc.perform(delete("/internal/v1/accounts/{id}", target)
                .header(InternalIdentityVerifier.USER_ID, SERVICE_ID.toString())
                .header(InternalIdentityVerifier.USER_ROLE, role)
                .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                .header(InternalIdentityVerifier.SIGNATURE, verifier.sign(SERVICE_ID.toString(), role, now)));
    }

    @Test
    void erasureAnonymizesTheAccountAndEndsAllSessions() throws Exception {
        String refreshToken = json(login(mockMvc, EMAIL, PASSWORD), "$.refreshToken");
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\"}"));

        erase(accountId, "SERVICE").andExpect(status().isNoContent());

        Account erased = accountRepository.findById(accountId).orElseThrow();
        assertThat(erased.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(erased.getEmail()).doesNotContain("alice").endsWith("@deleted.invalid");
        assertThat(resetTokenRepository.count()).isZero();
        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void erasedEmailCanBeUsedForANewAccount() throws Exception {
        erase(accountId, "SERVICE").andExpect(status().isNoContent());

        register(mockMvc, EMAIL, PASSWORD, "CANDIDATE");
    }

    @Test
    void erasureIsIdempotentAndIgnoresUnknownAccounts() throws Exception {
        erase(accountId, "SERVICE").andExpect(status().isNoContent());
        erase(accountId, "SERVICE").andExpect(status().isNoContent());
        erase(UUID.randomUUID(), "SERVICE").andExpect(status().isNoContent());
    }

    @Test
    void onlyBackendServicesCanCallTheInternalEndpoint() throws Exception {
        erase(accountId, "ADMIN").andExpect(status().isForbidden());
        erase(accountId, "CANDIDATE").andExpect(status().isForbidden());
        mockMvc.perform(delete("/internal/v1/accounts/{id}", accountId)).andExpect(status().isUnauthorized());

        assertThat(accountRepository.findById(accountId).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
