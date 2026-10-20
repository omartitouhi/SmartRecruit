package com.smartrecruit.user.controller;

import com.smartrecruit.user.dto.CompanyPublicResponse;
import com.smartrecruit.user.dto.CompanyRequest;
import com.smartrecruit.user.dto.CompanyResponse;
import com.smartrecruit.user.security.AuthenticatedUser;
import com.smartrecruit.user.service.CompanyPatchParser;
import com.smartrecruit.user.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Company profile of recruiters (BE-COMP-01..03).
 */
@RestController
@RequestMapping("/api/v1/users/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyPatchParser patchParser;

    /** Creates the recruiter's company; they become its first member — BE-COMP-01. */
    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CompanyRequest request) {
        return companyService.create(user.id(), request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('RECRUITER')")
    public CompanyResponse getMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return companyService.getMine(user.id());
    }

    /** Partial update (JSON Merge Patch) by a member of the company — BE-COMP-02. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public CompanyResponse patch(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                 @RequestBody JsonNode body) {
        return companyService.patch(user.id(), id, patchParser.parse(body));
    }

    /** Public view (no member ids) for any connected user, e.g. a candidate reading an offer. */
    @GetMapping("/{id}")
    public CompanyPublicResponse getPublic(@PathVariable UUID id) {
        return companyService.getPublic(id);
    }
}
