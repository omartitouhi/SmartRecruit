package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.PasswordResetTokenRepository;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import com.smartrecruit.auth.service.PasswordResetNotifier;
import com.smartrecruit.auth.support.MutableClock;
import com.smartrecruit.auth.support.MutableClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.smartrecruit.auth.support.AuthTestHelper.json;
import static com.smartrecruit.auth.support.AuthTestHelper.login;
import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({MutableClockConfig.class, PasswordResetIntegrationTest.CapturingNotifierConfig.class})
class PasswordResetIntegrationTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "Secret123!";
    private static final String NEW_PASSWORD = "BrandNew456!";

    @TestConfiguration
    static class CapturingNotifierConfig {
        @Bean
        @Primary
        CapturingNotifier capturingNotifier() {
            return new CapturingNotifier();
        }
    }

    static class CapturingNotifier implements PasswordResetNotifier {
        final List<String> links = new ArrayList<>();

        @Override
        public void sendResetLink(String email, String resetLink) {
            links.add(resetLink);
        }

        String lastToken() {
            String link = links.getLast();
            return UriComponentsBuilder.fromUriString(link).build().getQueryParams().getFirst("token");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private CapturingNotifier notifier;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        notifier.links.clear();
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        register(mockMvc, EMAIL, PASSWORD, "CANDIDATE");
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, newPassword)));
    }

    @Test
    void forgotPasswordGeneratesLinkForExistingEmail() throws Exception {
        forgot(EMAIL).andExpect(status().isAccepted());

        assertThat(notifier.links).hasSize(1);
        assertThat(notifier.links.getFirst()).startsWith("http://localhost:5173/reset-password?token=");
        assertThat(resetTokenRepository.findAll().getFirst().getExpiresAt())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));
    }

    @Test
    void forgotPasswordForUnknownEmailLooksTheSameButSendsNothing() throws Exception {
        forgot("nobody@example.com").andExpect(status().isAccepted());

        assertThat(notifier.links).isEmpty();
        assertThat(resetTokenRepository.count()).isZero();
    }

    @Test
    void validLinkUpdatesPasswordAndIsInvalidatedAfterUse() throws Exception {
        forgot(EMAIL);
        String token = notifier.lastToken();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login(mockMvc, EMAIL, NEW_PASSWORD).andExpect(status().isOk());
        reset(token, "Another789!").andExpect(status().isBadRequest());
    }

    @Test
    void linkExpiresAfter30Minutes() throws Exception {
        forgot(EMAIL);
        clock.advance(Duration.ofMinutes(31));

        reset(notifier.lastToken(), NEW_PASSWORD).andExpect(status().isBadRequest());
        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newRequestInvalidatesPreviousLink() throws Exception {
        forgot(EMAIL);
        String first = notifier.lastToken();
        forgot(EMAIL);
        String second = notifier.lastToken();

        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resetRevokesExistingSessions() throws Exception {
        String refreshToken = json(login(mockMvc, EMAIL, PASSWORD), "$.refreshToken");
        forgot(EMAIL);

        reset(notifier.lastToken(), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void weakNewPasswordIsRejected() throws Exception {
        forgot(EMAIL);

        reset(notifier.lastToken(), "short").andExpect(status().isBadRequest());
        login(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        reset("forged-token", NEW_PASSWORD).andExpect(status().isBadRequest());
    }
}
