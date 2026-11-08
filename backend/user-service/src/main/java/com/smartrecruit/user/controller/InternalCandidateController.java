package com.smartrecruit.user.controller;

import com.smartrecruit.user.dto.CandidateReadinessResponse;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Backend-to-backend candidate endpoints; {@code /internal/**} is not routed by the api-gateway and
 * requires a signed {@code SERVICE} identity.
 */
@RestController
@RequestMapping("/internal/v1/candidates")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalCandidateController {

    private final CandidateProfileRepository profileRepository;

    /** Used by application-service to check a candidate can apply; 404 if there is no profile. */
    @GetMapping("/{id}/readiness")
    @Transactional(readOnly = true)
    public CandidateReadinessResponse readiness(@PathVariable UUID id) {
        return profileRepository.findById(id).map(CandidateReadinessResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Profil candidat introuvable"));
    }
}
