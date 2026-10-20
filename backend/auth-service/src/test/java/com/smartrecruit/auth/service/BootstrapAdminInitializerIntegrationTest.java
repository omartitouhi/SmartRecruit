package com.smartrecruit.auth.service;

import com.smartrecruit.auth.config.AuthProperties;
import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.PasswordResetTokenRepository;
import com.smartrecruit.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.smartrecruit.auth.support.AuthTestHelper.login;
import static com.smartrecruit.auth.support.AuthTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bootstrap administrator (contract §4): created once at startup from the configured credentials. */
@SpringBootTest(properties = {
        "auth.bootstrap-admin.email= Root@Example.com ",
        "auth.bootstrap-admin.password=Adm1nPass!"})
@AutoConfigureMockMvc
class BootstrapAdminInitializerIntegrationTest {

    private static final String EMAIL = "root@example.com";

    @Autowired
    private BootstrapAdminInitializer initializer;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void createsAnActiveAdminWhoCanLogIn() throws Exception {
        initializer.run(null);

        Account admin = accountRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isNotEqualTo("Adm1nPass!");
        login(mockMvc, EMAIL, "Adm1nPass!").andExpect(status().isOk());
    }

    @Test
    void isIdempotentAndNeverOverwritesAnExistingAccount() throws Exception {
        initializer.run(null);
        String hash = accountRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();

        initializer.run(null);

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(accountRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void leavesAnExistingNonAdminAccountUntouched() throws Exception {
        register(mockMvc, EMAIL, "Secret123!", "CANDIDATE");

        initializer.run(null);

        assertThat(accountRepository.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.CANDIDATE);
    }

    @Test
    void doesNothingWhenEmailOrPasswordIsMissing() {
        for (AuthProperties.BootstrapAdmin missing : new AuthProperties.BootstrapAdmin[]{
                null, new AuthProperties.BootstrapAdmin("", ""),
                new AuthProperties.BootstrapAdmin(EMAIL, " "), new AuthProperties.BootstrapAdmin(null, "Adm1nPass!")}) {
            new BootstrapAdminInitializer(accountRepository, passwordEncoder,
                    new AuthProperties(null, null, null, missing)).run(null);
        }

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    void registrationStillRefusesTheAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("{\"email\": \"x@example.com\", \"password\": \"Secret123!\", \"role\": \"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }
}
