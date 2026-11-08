package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.Company;

import java.util.UUID;

/**
 * Minimal company data other services need to display an offer (BE-REC-01).
 */
public record CompanySummaryResponse(UUID id, String name, String logoUrl) {

    public static CompanySummaryResponse from(Company company) {
        return new CompanySummaryResponse(company.getId(), company.getName(), company.getLogoUrl());
    }
}
