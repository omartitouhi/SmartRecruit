package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.dto.AccountResponse;
import com.smartrecruit.dashboard.dto.AccountStatusRequest;
import com.smartrecruit.dashboard.dto.PageResponse;
import com.smartrecruit.dashboard.security.AuthenticatedUser;
import com.smartrecruit.dashboard.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** BE-ADMIN-01 — administrators list users and disable or re-enable accounts. */
@RestController
@RequestMapping("/api/v1/dashboard/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService userService;

    @GetMapping
    public PageResponse<AccountResponse> list(@RequestParam(required = false) String role,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return userService.list(role, status, page, size);
    }

    @PatchMapping("/{id}/status")
    public AccountResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                        @Valid @RequestBody AccountStatusRequest request) {
        return userService.updateStatus(admin.id(), id, request.status());
    }
}
