package com.smartrecruit.auth.service;

import com.smartrecruit.auth.config.AuthProperties;
import com.smartrecruit.auth.dto.ForgotPasswordRequest;
import com.smartrecruit.auth.dto.ResetPasswordRequest;
import com.smartrecruit.auth.exception.ApiException;
import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.PasswordResetToken;
import com.smartrecruit.auth.repository.AccountRepository;
import com.smartrecruit.auth.repository.PasswordResetTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;

/**
 * Password reset flow (BE-AUTH-06): single-use link valid 30 minutes.
 */
@Service
public class PasswordResetService {

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetNotifier notifier;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties.PasswordReset settings;
    private final Clock clock;

    public PasswordResetService(AccountRepository accountRepository, PasswordResetTokenRepository resetTokenRepository,
                                RefreshTokenService refreshTokenService, PasswordResetNotifier notifier,
                                PasswordEncoder passwordEncoder, AuthProperties properties, Clock clock) {
        this.accountRepository = accountRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.notifier = notifier;
        this.passwordEncoder = passwordEncoder;
        this.settings = properties.passwordReset();
        this.clock = clock;
    }

    /**
     * Silently does nothing for an unknown or inactive email so the endpoint cannot be used
     * to discover which emails have an account.
     */
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        accountRepository.findByEmail(AuthService.normalizeEmail(request.email()))
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(this::issueResetLink);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokenRepository.findByTokenHash(SecureTokens.hash(request.token()))
                .filter(found -> found.isUsable(now))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Lien de réinitialisation invalide ou expiré"));
        Account account = accountRepository.findById(token.getAccountId())
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Lien de réinitialisation invalide ou expiré"));

        token.setUsedAt(now);
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setFailedLoginAttempts(0);
        account.setFirstFailedLoginAt(null);
        account.setLockedUntil(null);
        refreshTokenService.revokeAll(account.getId());
    }

    private void issueResetLink(Account account) {
        Instant now = clock.instant();
        resetTokenRepository.invalidateAllPending(account.getId(), now);
        String rawToken = SecureTokens.generate();
        resetTokenRepository.save(PasswordResetToken.builder()
                .tokenHash(SecureTokens.hash(rawToken))
                .accountId(account.getId())
                .expiresAt(now.plus(settings.tokenTtl()))
                .build());
        String link = UriComponentsBuilder.fromUriString(settings.linkBaseUrl())
                .queryParam("token", rawToken)
                .toUriString();
        notifier.sendResetLink(account.getEmail(), link);
    }
}
