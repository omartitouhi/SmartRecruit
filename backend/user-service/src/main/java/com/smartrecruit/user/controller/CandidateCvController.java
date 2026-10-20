package com.smartrecruit.user.controller;

import com.smartrecruit.user.dto.CandidateProfileResponse;
import com.smartrecruit.user.dto.CvSuggestionsResponse;
import com.smartrecruit.user.security.AuthenticatedUser;
import com.smartrecruit.user.service.CandidateCvService;
import com.smartrecruit.user.service.CvSuggestionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/candidates")
@RequiredArgsConstructor
public class CandidateCvController {

    private final CandidateCvService cvService;
    private final CvSuggestionsService suggestionsService;

    /** Upload of the connected candidate's CV (PDF, 5 MB max) — BE-USER-02. */
    @PostMapping(path = "/me/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateProfileResponse upload(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam(name = "file", required = false) MultipartFile file) {
        return cvService.upload(user.id(), file);
    }

    /** Data the AI extracted from the last analysed CV, to validate before saving it on the profile — BE-CV-02. */
    @GetMapping("/me/cv-suggestions")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CvSuggestionsResponse suggestions(@AuthenticationPrincipal AuthenticatedUser user) {
        return suggestionsService.suggestionsFor(user.id());
    }

    /**
     * Download of a CV: target of {@code cvUrl}. Allowed for the candidate themself, recruiters and admins.
     */
    @GetMapping("/{candidateId}/cv")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN') or (hasRole('CANDIDATE') and #candidateId == principal.id())")
    public ResponseEntity<byte[]> download(@PathVariable UUID candidateId) {
        byte[] content = cvService.download(candidateId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("cv-" + candidateId + ".pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }
}
