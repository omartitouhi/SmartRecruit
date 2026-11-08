package com.smartrecruit.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dev-only delivery: the link is written to the logs instead of being emailed.
 */
@Slf4j
@Component
public class LoggingCompanyInvitationNotifier implements CompanyInvitationNotifier {

    @Override
    public void sendInvitation(String email, String companyName, String acceptLink) {
        // shortcut: logs the raw token at INFO because the project has no mail server; swap this bean for a mailer
        log.info("[DEV] Invitation de {} à rejoindre l'entreprise {} : {}", email, companyName, acceptLink);
    }
}
