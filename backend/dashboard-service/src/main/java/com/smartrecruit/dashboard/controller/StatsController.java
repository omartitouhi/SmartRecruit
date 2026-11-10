package com.smartrecruit.dashboard.controller;

import com.smartrecruit.dashboard.dto.CompanyStatsResponse;
import com.smartrecruit.dashboard.dto.GlobalStatsResponse;
import com.smartrecruit.dashboard.exception.ApiException;
import com.smartrecruit.dashboard.security.AuthenticatedUser;
import com.smartrecruit.dashboard.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * BE-DASH-01 / BE-DASH-02 — dashboard statistics. Optional {@code from}/{@code to} (ISO dates, inclusive) filter
 * the application figures on the date the applications were made.
 */
@RestController
@RequestMapping("/api/v1/dashboard/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/global")
    @PreAuthorize("hasRole('ADMIN')")
    public GlobalStatsResponse global(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkPeriod(from, to);
        return statsService.global(from, to);
    }

    @GetMapping("/company/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public CompanyStatsResponse company(
            @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkPeriod(from, to);
        return statsService.company(caller, id, from, to);
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La date de début doit précéder la date de fin");
        }
    }
}
