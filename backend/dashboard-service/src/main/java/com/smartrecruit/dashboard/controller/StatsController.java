package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.dto.CompanyStatsResponse;
import com.smartrecruit.dashboard.dto.GlobalStatsResponse;
import com.smartrecruit.dashboard.security.AuthenticatedUser;
import com.smartrecruit.dashboard.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** BE-DASH-01 / BE-DASH-02 — dashboard statistics. */
@RestController
@RequestMapping("/api/v1/dashboard/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/global")
    @PreAuthorize("hasRole('ADMIN')")
    public GlobalStatsResponse global() {
        return statsService.global();
    }

    @GetMapping("/company/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public CompanyStatsResponse company(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return statsService.company(caller, id);
    }
}
