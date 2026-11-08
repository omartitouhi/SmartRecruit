package com.smartrecruit.job.controller;

import com.smartrecruit.job.service.SavedJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Personal data of a candidate held by job-service, erased by user-service when the candidate
 * deletes their account (BE-SEC-02, EXT-SAVED-01). Not routed by the api-gateway.
 */
@RestController
@RequestMapping("/internal/v1/candidates")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalCandidateController {

    private final SavedJobService savedJobService;

    /** Idempotent: 204 even when the candidate saved nothing. */
    @DeleteMapping("/{candidateId}/saved-jobs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eraseSavedJobs(@PathVariable UUID candidateId) {
        savedJobService.eraseCandidate(candidateId);
    }
}
