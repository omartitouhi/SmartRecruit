package com.smartrecruit.user.controller;

import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Counters for dashboard-service; {@code /internal/**} requires a signed {@code SERVICE} identity.
 */
@RestController
@RequestMapping("/internal/v1/stats")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalStatsController {

    public record Stats(long candidateProfiles, long companies) {
    }

    private final CandidateProfileRepository profileRepository;
    private final CompanyRepository companyRepository;

    @GetMapping
    public Stats stats() {
        return new Stats(profileRepository.count(), companyRepository.count());
    }
}
