package com.smartrecruit.auth.service;

import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Effective erasure of an account on GDPR request (BE-SEC-02), called by user-service once the
 * candidate deleted their account. The row is kept with status {@code DELETED} so ids referenced
 * elsewhere stay consistent, but it no longer holds any personal data and can never log in again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    /** Idempotent; an unknown id is ignored (the account may never have existed or be already erased). */
    @Transactional
    public void erase(UUID accountId) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setEmail("deleted-" + accountId + "@deleted.invalid");
            // Random secret nobody knows: the account cannot authenticate anymore.
            account.setPasswordHash(passwordEncoder.encode(SecureTokens.generate()));
            account.setStatus(AccountStatus.DELETED);
            account.setFailedLoginAttempts(0);
            account.setFirstFailedLoginAt(null);
            account.setLockedUntil(null);
            log.info("Compte {} anonymisé (droit à l'oubli)", accountId);
        });
        refreshTokenService.revokeAll(accountId);
        resetTokenRepository.deleteAllByAccount(accountId);
    }
}
