package com.smartrecruit.auth.controller;

import com.smartrecruit.auth.dto.AccountResponse;
import com.smartrecruit.auth.dto.AccountStatsResponse;
import com.smartrecruit.auth.dto.AccountStatusRequest;
import com.smartrecruit.auth.dto.PageResponse;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import com.smartrecruit.auth.service.AccountAdminService;
import com.smartrecruit.auth.service.AccountErasureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AccountAdminService adminService;

    @GetMapping
    public PageResponse<AccountResponse> list(@RequestParam(required = false) Role role,
                                              @RequestParam(required = false) AccountStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return adminService.list(role, status, page, size);
    }

    @GetMapping("/stats")
    public AccountStatsResponse stats() {
        return adminService.stats();
    }

    @PatchMapping("/{accountId}/status")
    public AccountResponse updateStatus(@PathVariable UUID accountId, @Valid @RequestBody AccountStatusRequest request) {
        return adminService.updateStatus(accountId, request.status());
    }

    /** Erases the account's personal data (BE-SEC-02). Idempotent: always 204. */
    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId) {
        erasureService.erase(accountId);
    }
}
