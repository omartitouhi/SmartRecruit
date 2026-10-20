package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import com.smartrecruit.auth.security.InternalIdentityVerifier;
import com.smartrecruit.auth.service.JwtService;
import com.smartrecruit.auth.support.MutableClock;
import com.smartrecruit.auth.support.MutableClockConfig;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static com.smartrecruit.auth.support.AuthTestHelper.json;
import static com.smartrecruit.auth.support.AuthTestHelper.login;
import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
class LoginIntegrationTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "Secret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private InternalIdentityVerifier identityVerifier;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.now());
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        register(mockMvc, EMAIL, PASSWORD, "RECRUITER");
    }

    @Test
    void loginWithValidCredentialsReturnsSignedJwtAndRefreshToken() throws Exception {
        ResultActions result = login(mockMvc, "  Alice@Example.com ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        Account account = accountRepository.findByEmail(EMAIL).orElseThrow();
        Claims claims = jwtService.parse(json(result, "$.accessToken"));
        assertThat(claims.getSubject()).isEqualTo(account.getId().toString());
        assertThat(claims.get(JwtService.ROLE_CLAIM, String.class)).isEqualTo("RECRUITER");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofHours(1));
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findAll().getFirst().getTokenHash())
                .isNotEqualTo(json(result, "$.refreshToken"));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSame401() throws Exception {
        String wrongPassword = login(mockMvc, EMAIL, "WrongPass1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login(mockMvc, "nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).contains("Identifiants invalides");
        assertThat(unknownEmail).contains("Identifiants invalides");
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        Account account = accountRepository.findByEmail(EMAIL).orElseThrow();
        account.setStatus(AccountStatus.DISABLED);
        accountRepository.save(account);

        String body = login(mockMvc, EMAIL, PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("désactivé");
    }

    @Test
    void fiveFailuresWithin15MinutesLockTheAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(mockMvc, EMAIL, "WrongPass1").andExpect(status().isUnauthorized());
            clock.advance(Duration.ofMinutes(2));
        }

        login(mockMvc, EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(16));
        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void failuresSpreadBeyondTheWindowDoNotLock() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(mockMvc, EMAIL, "WrongPass1").andExpect(status().isUnauthorized());
        }
        clock.advance(Duration.ofMinutes(16));
        login(mockMvc, EMAIL, "WrongPass1").andExpect(status().isUnauthorized());

        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(mockMvc, EMAIL, "WrongPass1").andExpect(status().isUnauthorized());
        }
        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());
        login(mockMvc, EMAIL, "WrongPass1").andExpect(status().isUnauthorized());

        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void meEndpointReturnsAccountOfIdentityForwardedByGateway() throws Exception {
        // Simulates what the api-gateway forwards after validating the access token (BE-SEC-04).
        Claims claims = jwtService.parse(json(login(mockMvc, EMAIL, PASSWORD), "$.accessToken"));
        String role = claims.get(JwtService.ROLE_CLAIM, String.class);
        long now = clock.instant().getEpochSecond();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(InternalIdentityVerifier.USER_ID, claims.getSubject())
                        .header(InternalIdentityVerifier.USER_ROLE, role)
                        .header(InternalIdentityVerifier.TIMESTAMP, Long.toString(now))
                        .header(InternalIdentityVerifier.SIGNATURE, identityVerifier.sign(claims.getSubject(), role, now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("RECRUITER"));
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        login(mockMvc, "", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }
}
