package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.Company;

import java.util.UUID;

/**
 * Company as seen by any connected user: {@link CompanyResponse} without the member list.
 */
public record CompanyPublicResponse(UUID id, String name, String sector, String description, String logoUrl) {

    public static CompanyPublicResponse from(Company company) {
        return new CompanyPublicResponse(company.getId(), company.getName(), company.getSector(),
                company.getDescription(), company.getLogoUrl());
    }
}
