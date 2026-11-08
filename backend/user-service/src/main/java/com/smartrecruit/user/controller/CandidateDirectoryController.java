package com.smartrecruit.user.controller;

import com.smartrecruit.user.audit.AuditAction;
import com.smartrecruit.user.audit.AuditEmitter;
import com.smartrecruit.user.dto.CandidateProfileResponse;
import com.smartrecruit.user.security.AuthenticatedUser;
import com.smartrecruit.user.service.CandidateAccountDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Candidate profiles as seen by recruiters and admins (e.g. when reviewing an application).
 */
@RestController
@RequestMapping("/api/v1/users/candidates")
@PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
@RequiredArgsConstructor
public class CandidateDirectoryController {

    private final CandidateAccountDeletionService deletionService;
    private final AuditEmitter auditEmitter;

    /** Profile of a candidate, or {@code {"id": ..., "deleted": true}} if the account was deleted (BE-USER-04). */
    @GetMapping("/{candidateId}")
    public CandidateProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID candidateId) {
        CandidateProfileResponse profile = deletionService.viewCandidate(candidateId);
        auditEmitter.emit(user.id(), AuditAction.PROFILE_VIEWED, candidateId);
        return profile;
    }
}
