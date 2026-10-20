package com.smartrecruit.dashboard.service;

import com.smartrecruit.dashboard.client.AuthServiceClient;
import com.smartrecruit.dashboard.client.Upstream;
import com.smartrecruit.dashboard.dto.AccountResponse;
import com.smartrecruit.dashboard.dto.AuditEventRequest;
import com.smartrecruit.dashboard.dto.PageResponse;
import com.smartrecruit.dashboard.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** BE-ADMIN-01 — user management, delegated to auth-service. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AuthServiceClient authClient;
    private final AuditLogService auditLogService;

    public PageResponse<AccountResponse> list(String role, String status, int page, int size) {
        return Upstream.call(() -> authClient.list(role, status, page, size));
    }

    public AccountResponse updateStatus(UUID actorId, UUID accountId, String status) {
        if (actorId.equals(accountId) && "DISABLED".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas désactiver votre propre compte");
        }
        AccountResponse account = Upstream.call(() -> authClient.updateStatus(accountId, status));
        auditLogService.record(new AuditEventRequest(actorId, "USER_STATUS_CHANGED", "ACCOUNT", accountId));
        return account;
    }
}
