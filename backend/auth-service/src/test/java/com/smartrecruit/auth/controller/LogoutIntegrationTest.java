package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.model.RefreshToken;
import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.smartrecruit.auth.support.AuthTestHelper.json;
import static com.smartrecruit.auth.support.AuthTestHelper.login;
import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutIntegrationTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "Secret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        register(mockMvc, EMAIL, PASSWORD, "CANDIDATE");
    }

    private ResultActions send(String path, String refreshToken) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)));
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = json(login(mockMvc, EMAIL, PASSWORD), "$.refreshToken");

        send("/api/v1/auth/logout", refreshToken).andExpect(status().isNoContent());

        RefreshToken stored = refreshTokenRepository.findAll().getFirst();
        assertThat(stored.getRevokedAt()).isNotNull();
        send("/api/v1/auth/refresh", refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutOnlyRevokesTheGivenSession() throws Exception {
        String sessionA = json(login(mockMvc, EMAIL, PASSWORD), "$.refreshToken");
        String sessionB = json(login(mockMvc, EMAIL, PASSWORD), "$.refreshToken");

        send("/api/v1/auth/logout", sessionA).andExpect(status().isNoContent());

        send("/api/v1/auth/refresh", sessionA).andExpect(status().isUnauthorized());
        send("/api/v1/auth/refresh", sessionB).andExpect(status().isOk());
    }

    @Test
    void logoutIsIdempotent() throws Exception {
        String refreshToken = json(login(mockMvc, EMAIL, PASSWORD), "$.refreshToken");

        send("/api/v1/auth/logout", refreshToken).andExpect(status().isNoContent());
        send("/api/v1/auth/logout", refreshToken).andExpect(status().isNoContent());
        send("/api/v1/auth/logout", "unknown-token").andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
