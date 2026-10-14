package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.service.AccountErasureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Backend-to-backend endpoints. {@code /internal/**} is not routed by the api-gateway and requires
 * a signed {@code SERVICE} identity.
 */
@RestController
@RequestMapping("/internal/v1/accounts")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalAccountController {

    private final AccountErasureService erasureService;

    /** Erases the account's personal data (BE-SEC-02). Idempotent: always 204. */
    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId) {
        erasureService.erase(accountId);
    }
}
