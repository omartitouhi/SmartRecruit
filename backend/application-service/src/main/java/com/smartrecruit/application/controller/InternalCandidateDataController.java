package com.smartrecruit.application.controller;

import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.repository.ApplicationNoteRepository;
import com.smartrecruit.application.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Right to be forgotten (BE-SEC-02, extended by EXT-APP-01/03): called by user-service once a candidate deleted
 * their account. Clears their cover letters and the team notes about their applications; idempotent.
 */
@RestController
@RequestMapping("/internal/v1/candidates")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class InternalCandidateDataController {

    private final ApplicationRepository applications;
    private final ApplicationNoteRepository notes;

    @DeleteMapping("/{candidateId}/data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void erase(@PathVariable UUID candidateId) {
        List<UUID> ids = applications.findByCandidateId(candidateId).stream().map(Application::getId).toList();
        if (!ids.isEmpty()) {
            notes.deleteByApplicationIds(ids);
        }
        applications.clearCoverLetters(candidateId);
    }
}
