package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.dto.AuditEventRequest;
import com.smartrecruit.dashboard.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend-to-backend endpoints. {@code /internal/**} is not routed by the api-gateway and requires
 * a signed {@code SERVICE} identity.
 */
@RestController
@RequestMapping("/internal/v1/audit-events")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalAuditController {

    private final AuditLogService auditLogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void record(@Valid @RequestBody AuditEventRequest event) {
        auditLogService.record(event);
    }
}
