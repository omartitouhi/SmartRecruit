package com.smartrecruit.user.service;

import com.smartrecruit.user.dto.CandidateProfileResponse;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.model.DeletedCandidate;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import com.smartrecruit.user.repository.DeletedCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Right to be forgotten (BE-USER-04): the candidate's personal data held by user-service — profile
 * and CV file — is removed immediately; only a tombstone without personal data is kept.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateAccountDeletionService {

    private final CandidateProfileRepository profileRepository;
    private final DeletedCandidateRepository deletedCandidateRepository;
    private final CandidateCvService cvService;
    private final Clock clock;

    /** Idempotent: deleting an already deleted account does nothing more. */
    @Transactional
    public void deleteAccount(UUID accountId) {
        if (deletedCandidateRepository.existsById(accountId)) {
            return;
        }
        profileRepository.findById(accountId).ifPresent(profile -> {
            cvService.deleteFileAfterCommit(profile.getCvStorageKey());
            profileRepository.delete(profile);
        });
        deletedCandidateRepository.save(new DeletedCandidate(accountId, clock.instant()));
        log.info("Données personnelles du candidat {} supprimées (droit à l'oubli)", accountId);
    }

    /**
     * Candidate profile as seen by a recruiter or an admin, e.g. from an application.
     * A deleted account is shown as "compte supprimé" without any personal data.
     */
    @Transactional(readOnly = true)
    public CandidateProfileResponse viewCandidate(UUID candidateId) {
        if (deletedCandidateRepository.existsById(candidateId)) {
            return CandidateProfileResponse.deletedAccount(candidateId);
        }
        return profileRepository.findById(candidateId)
                .map(CandidateProfileResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Candidat introuvable"));
    }
}
