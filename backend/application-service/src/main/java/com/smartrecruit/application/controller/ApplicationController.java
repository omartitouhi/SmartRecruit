package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.ApplicationResponse;
import com.smartrecruit.application.dto.ApplyRequest;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.dto.StatusChangeRequest;
import com.smartrecruit.application.security.AuthenticatedUser;
import com.smartrecruit.application.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse apply(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ApplyRequest request) {
        return ApplicationResponse.from(applicationService.apply(user.id(), request.jobId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public List<ApplicationResponse> receivedFor(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam UUID jobId) {
        return applicationService.receivedFor(user, jobId).stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<ApplicationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return applicationService.mine(user.id()).stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/match-preview")
    @PreAuthorize("hasRole('CANDIDATE')")
    public MatchScoreDto matchPreview(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam UUID jobId) {
        return applicationService.matchPreview(user.id(), jobId);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ApplicationResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                            @Valid @RequestBody StatusChangeRequest request) {
        return ApplicationResponse.from(applicationService.changeStatus(user.id(), id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CANDIDATE')")
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        applicationService.withdraw(user.id(), id);
    }
}
