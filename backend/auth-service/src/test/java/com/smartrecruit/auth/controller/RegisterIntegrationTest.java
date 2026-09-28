package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.Role;
import com.smartrecruit.auth.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegisterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
    }

    private String body(String email, String password, String role) {
        return """
                {"email": "%s", "password": "%s", "role": "%s"}
                """.formatted(email, password, role);
    }

    @Test
    void registerCreatesAccountWithHashedPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Alice@Example.com", "Secret123!", "CANDIDATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        Account saved = accountRepository.findByEmail("alice@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.CANDIDATE);
        assertThat(saved.getPasswordHash()).isNotEqualTo("Secret123!").startsWith("$2");
        assertThat(passwordEncoder.matches("Secret123!", saved.getPasswordHash())).isTrue();
    }

    @Test
    void registerWithRecruiterRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob@example.com", "Secret123!", "RECRUITER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    @Test
    void registerWithExistingEmailReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice@example.com", "Secret123!", "CANDIDATE")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ALICE@example.com", "Other1234", "RECRUITER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Un compte existe déjà avec l'email alice@example.com"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void registerWithWeakPasswordReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice@example.com", "short", "CANDIDATE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    void registerWithInvalidFieldsReturnsOneErrorPerField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void registerAsAdminIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("eve@example.com", "Secret123!", "ADMIN")))
                .andExpect(status().isBadRequest());

        assertThat(accountRepository.count()).isZero();
    }
}
