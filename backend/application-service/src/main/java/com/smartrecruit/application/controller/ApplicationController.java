package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.ApplicationResponse;
import com.smartrecruit.application.dto.ApplyRequest;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.security.AuthenticatedUser;
import com.smartrecruit.application.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/match-preview")
    @PreAuthorize("hasRole('CANDIDATE')")
    public MatchScoreDto matchPreview(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam UUID jobId) {
        return applicationService.matchPreview(user.id(), jobId);
    }
}
