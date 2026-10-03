package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.dto.AccountResponse;
import com.smartrecruit.auth.dto.ForgotPasswordRequest;
import com.smartrecruit.auth.dto.LoginRequest;
import com.smartrecruit.auth.dto.RefreshTokenRequest;
import com.smartrecruit.auth.dto.RegisterRequest;
import com.smartrecruit.auth.dto.ResetPasswordRequest;
import com.smartrecruit.auth.dto.TokenResponse;
import com.smartrecruit.auth.security.AuthenticatedUser;
import com.smartrecruit.auth.service.AuthService;
import com.smartrecruit.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Account of the caller, identified by the access token. */
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.getAccount(user.id());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    /** Idempotent: always 204, even for an unknown or already revoked token. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    /** Always 202, whether or not the email matches an account (anti-enumeration). */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
    }
}
