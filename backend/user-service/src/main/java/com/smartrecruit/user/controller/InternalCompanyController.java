package com.smartrecruit.user.controller;

import com.smartrecruit.user.dto.CompanySummaryResponse;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Backend-to-backend endpoints. {@code /internal/**} is not routed by the api-gateway and requires
 * a signed {@code SERVICE} identity.
 */
@RestController
@RequestMapping("/internal/v1/companies")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalCompanyController {

    static final int MAX_IDS = 100;

    private final CompanyRepository companyRepository;

    /** Batch lookup used by job-service to enrich offers (BE-REC-01). Unknown ids are skipped. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<CompanySummaryResponse> findByIds(@RequestParam("ids") Set<UUID> ids) {
        if (ids.size() > MAX_IDS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "100 entreprises maximum par requête");
        }
        return companyRepository.findAllById(ids).stream().map(CompanySummaryResponse::from).toList();
    }

    /** Company of a recruiter, used by dashboard-service and job-service; 404 if the account has none. */
    @GetMapping("/by-member/{accountId}")
    @Transactional(readOnly = true)
    public CompanySummaryResponse findByMember(@PathVariable UUID accountId) {
        return companyRepository.findByMember(accountId).map(CompanySummaryResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Aucune entreprise pour ce compte"));
    }
}
