package com.smartrecruit.auth.service;

import com.smartrecruit.auth.config.AuthProperties;
import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import com.smartrecruit.auth.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first ADMIN account (there is no public way to register one) from
 * {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}. Never touches an account that already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            return;
        }
        String email = AuthService.normalizeEmail(admin.email());
        if (accountRepository.existsByEmail(email)) {
            return;
        }
        accountRepository.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(admin.password()))
                .role(Role.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());
        log.info("Compte administrateur de démarrage créé");
    }
}
