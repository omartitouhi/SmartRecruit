package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.Company;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Public shape of {@link Company}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyResponse {

    private UUID id;
    private String name;
    private String sector;
    private String description;
    private String logoUrl;
    private List<UUID> members;

    public static CompanyResponse from(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .sector(company.getSector())
                .description(company.getDescription())
                .logoUrl(company.getLogoUrl())
                .members(company.getMembers())
                .build();
    }
}
