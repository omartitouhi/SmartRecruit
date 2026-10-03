package com.smartrecruit.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dev-only delivery: the link is written to the logs instead of being emailed.
 */
@Slf4j
@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    @Override
    public void sendResetLink(String email, String resetLink) {
        log.info("[DEV] Lien de réinitialisation du mot de passe pour {} : {}", email, resetLink);
    }
}
