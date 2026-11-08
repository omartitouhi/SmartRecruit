package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import com.smartrecruit.auth.service.JwtService;
import com.smartrecruit.auth.support.MutableClock;
import com.smartrecruit.auth.support.MutableClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static com.smartrecruit.auth.support.AuthTestHelper.json;
import static com.smartrecruit.auth.support.AuthTestHelper.login;
import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
class RefreshIntegrationTest {

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

    private String refreshToken;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.now());
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        register(mockMvc, EMAIL, PASSWORD, "CANDIDATE");
        refreshToken = json(login(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk()), "$.refreshToken");
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(token)));
    }

    @Test
    void validRefreshTokenReturnsNewAccessToken() throws Exception {
        clock.advance(Duration.ofMinutes(30));

        ResultActions result = refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        Account account = accountRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(jwtService.parse(json(result, "$.accessToken")).getSubject())
                .isEqualTo(account.getId().toString());
        assertThat(json(result, "$.refreshToken")).isNotEqualTo(refreshToken);
    }

    @Test
    void refreshTokenIsSingleUse() throws Exception {
        String rotated = json(refresh(refreshToken).andExpect(status().isOk()), "$.refreshToken");

        refresh(refreshToken).andExpect(status().isUnauthorized());
        refresh(rotated).andExpect(status().isOk());
    }

    @Test
    void expiredRefreshTokenReturns401() throws Exception {
        clock.advance(Duration.ofDays(7).plusMinutes(1));

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void revokedRefreshTokenReturns401() throws Exception {
        refreshTokenRepository.findAll().forEach(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenReturns401() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotRefresh() throws Exception {
        Account account = accountRepository.findByEmail(EMAIL).orElseThrow();
        account.setStatus(AccountStatus.DISABLED);
        accountRepository.save(account);

        refresh(refreshToken).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("désactivé")));
    }

    @Test
    void blankRefreshTokenReturns400() throws Exception {
        refresh("").andExpect(status().isBadRequest());
    }
}
