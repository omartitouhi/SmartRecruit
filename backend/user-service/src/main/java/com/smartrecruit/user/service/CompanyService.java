package com.smartrecruit.user.service;

import com.smartrecruit.user.dto.CompanyPublicResponse;
import com.smartrecruit.user.dto.CompanyRequest;
import com.smartrecruit.user.dto.CompanyResponse;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.model.Company;
import com.smartrecruit.user.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Company profiles (BE-COMP-01): a recruiter creates their company and becomes its first member.
 * One company per recruiter in the MVP.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    static final String ALREADY_IN_COMPANY = "Vous appartenez déjà à une entreprise";

    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyResponse create(UUID accountId, CompanyRequest request) {
        if (companyRepository.findByMember(accountId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_IN_COMPANY);
        }
        Company company = Company.builder()
                .id(UUID.randomUUID())
                .name(request.name().trim())
                .sector(request.sector().trim())
                .description(CandidateProfileService.blankToNull(request.description()))
                .logoUrl(CandidateProfileService.blankToNull(request.logoUrl()))
                .members(new ArrayList<>(List.of(accountId)))
                .build();
        try {
            return CompanyResponse.from(companyRepository.saveAndFlush(company));
        } catch (DataIntegrityViolationException e) {
            // two concurrent creations by the same recruiter: the unique member column rejected the second
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_IN_COMPANY);
        }
    }

    /** Company of the connected recruiter, with its member list. */
    @Transactional(readOnly = true)
    public CompanyResponse getMine(UUID accountId) {
        return companyRepository.findByMember(accountId).map(CompanyResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Aucune entreprise : créez-la avec POST /api/v1/users/companies"));
    }

    @Transactional(readOnly = true)
    public CompanyPublicResponse getPublic(UUID id) {
        return companyRepository.findById(id).map(CompanyPublicResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
    }
}
