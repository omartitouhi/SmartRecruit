package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static com.smartrecruit.auth.support.AuthTestHelper.login;
import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-SEC-03 on the authentication endpoints: SQL injection attempts are inert (prepared statements
 * only) and passwords are never altered by the input sanitization.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InjectionProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() throws Exception {
        accountRepository.deleteAll();
        register(mockMvc, "alice@example.com", "Secret123!", "CANDIDATE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "' OR '1'='1",
            "alice@example.com' --",
            "alice@example.com'; DROP TABLE accounts; --",
            "\\\" OR 1=1 --"
    })
    void sqlInjectionInLoginIsRejectedLikeAnyWrongCredential(String payload) throws Exception {
        login(mockMvc, payload, "' OR '1'='1").andExpect(status().isUnauthorized());
        login(mockMvc, "alice@example.com", payload).andExpect(status().isUnauthorized());

        assertThat(accountRepository.count()).isEqualTo(1);
        login(mockMvc, "alice@example.com", "Secret123!").andExpect(status().isOk());
    }

    @Test
    void passwordsWithMarkupCharactersAreKeptVerbatim() throws Exception {
        register(mockMvc, "bob@example.com", "<b>P@ss&word</b>", "RECRUITER");

        login(mockMvc, "bob@example.com", "<b>P@ss&word</b>").andExpect(status().isOk());
        login(mockMvc, "bob@example.com", "P@ss&word").andExpect(status().isUnauthorized());
    }

    @Test
    void loginDoesNotCreateASessionCookie() throws Exception {
        login(mockMvc, "alice@example.com", "Secret123!")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
