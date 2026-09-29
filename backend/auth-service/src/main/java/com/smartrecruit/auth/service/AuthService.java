package com.smartrecruit.auth.service;

import com.smartrecruit.auth.config.AuthProperties;
import com.smartrecruit.auth.dto.AccountResponse;
import com.smartrecruit.auth.dto.LoginRequest;
import com.smartrecruit.auth.dto.RegisterRequest;
import com.smartrecruit.auth.dto.TokenResponse;
import com.smartrecruit.auth.exception.ApiException;
import com.smartrecruit.auth.exception.EmailAlreadyUsedException;
import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import com.smartrecruit.auth.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Identifiants invalides";
    static final String ACCOUNT_LOCKED = "Trop de tentatives échouées, compte temporairement bloqué. Réessayez plus tard.";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties.Lockout lockout;
    private final Clock clock;
    // Compared against when the email is unknown so both failure paths cost one BCrypt check.
    private final String dummyPasswordHash;

    public AuthService(AccountRepository accountRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService, AuthProperties properties, Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.lockout = properties.lockout();
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AccountResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le rôle ADMIN ne peut pas être choisi à l'inscription");
        }
        String email = normalizeEmail(request.email());
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        Account account = Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(AccountStatus.ACTIVE)
                .build();
        return AccountResponse.from(accountRepository.save(account));
    }

    /**
     * Failed attempts must be persisted even though the method ends with an exception,
     * hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest request) {
        Instant now = clock.instant();
        Optional<Account> found = accountRepository.findByEmail(normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        Account account = found.get();
        if (account.isLocked(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            registerFailedAttempt(account, now);
            throw invalidCredentials();
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw invalidCredentials();
        }
        account.setFailedLoginAttempts(0);
        account.setFirstFailedLoginAt(null);
        account.setLockedUntil(null);
        return issueTokens(account);
    }

    TokenResponse issueTokens(Account account) {
        String accessToken = jwtService.generateAccessToken(account);
        String refreshToken = refreshTokenService.issue(account.getId());
        return TokenResponse.bearer(accessToken, refreshToken, jwtService.getAccessTokenTtl().toSeconds());
    }

    private void registerFailedAttempt(Account account, Instant now) {
        Instant windowStart = account.getFirstFailedLoginAt();
        if (windowStart == null || windowStart.plus(lockout.window()).isBefore(now)) {
            account.setFailedLoginAttempts(1);
            account.setFirstFailedLoginAt(now);
        } else {
            account.setFailedLoginAttempts(account.getFailedLoginAttempts() + 1);
        }
        if (account.getFailedLoginAttempts() >= lockout.maxAttempts()) {
            account.setLockedUntil(now.plus(lockout.duration()));
            account.setFailedLoginAttempts(0);
            account.setFirstFailedLoginAt(null);
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
