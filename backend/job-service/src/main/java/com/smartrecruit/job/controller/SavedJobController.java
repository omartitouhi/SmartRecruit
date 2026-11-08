package com.smartrecruit.job.controller;

import com.smartrecruit.job.dto.JobSummaryResponse;
import com.smartrecruit.job.security.AuthenticatedUser;
import com.smartrecruit.job.service.SavedJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Bookmarked offers of the connected candidate — EXT-SAVED-01. {@code GET /api/v1/jobs/saved} is a
 * literal path, so it wins over {@code GET /api/v1/jobs/{id}}.
 */
@RestController
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class SavedJobController {

    private final SavedJobService savedJobService;

    @GetMapping("/api/v1/jobs/saved")
    public List<JobSummaryResponse> mySavedJobs(@AuthenticationPrincipal AuthenticatedUser user) {
        return savedJobService.list(user.id());
    }

    @PutMapping("/api/v1/jobs/{id}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        savedJobService.save(user.id(), id);
    }

    @DeleteMapping("/api/v1/jobs/{id}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        savedJobService.remove(user.id(), id);
    }
}
