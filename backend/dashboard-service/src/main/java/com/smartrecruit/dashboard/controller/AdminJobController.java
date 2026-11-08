package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.dto.ModerationRequest;
import com.smartrecruit.dashboard.security.AuthenticatedUser;
import com.smartrecruit.dashboard.service.AdminJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** BE-ADMIN-02 — administrators withdraw non-compliant job offers. */
@RestController
@RequestMapping("/api/v1/dashboard/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminJobController {

    private final AdminJobService jobService;

    @PatchMapping("/{id}/moderation")
    public Map<String, Object> moderate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                        @Valid @RequestBody ModerationRequest request) {
        return jobService.moderate(admin.id(), id, request.reason());
    }
}
