package com.smartrecruit.application.controller;

import com.smartrecruit.application.dto.ApplicationResponse;
import com.smartrecruit.application.dto.ApplyRequest;
import com.smartrecruit.application.dto.InterviewRequest;
import com.smartrecruit.application.dto.MatchScoreDto;
import com.smartrecruit.application.dto.NoteRequest;
import com.smartrecruit.application.dto.NoteResponse;
import com.smartrecruit.application.dto.NoteSummary;
import com.smartrecruit.application.dto.StatusChangeRequest;
import com.smartrecruit.application.dto.UpcomingInterviewResponse;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.security.AuthenticatedUser;
import com.smartrecruit.application.service.ApplicationNoteService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationNoteService noteService;

    /** BE-APP-01, with an optional cover letter (EXT-APP-01). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse apply(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ApplyRequest request) {
        return ApplicationResponse.from(applicationService.apply(user.id(), request.jobId(), request.coverLetter()));
    }

    /** BE-APP-03, each application with its team notes summary (EXT-APP-03). */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public List<ApplicationResponse> receivedFor(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam UUID jobId) {
        List<Application> received = applicationService.receivedFor(user, jobId);
        Map<UUID, NoteSummary> notes = noteService.summaries(received.stream().map(Application::getId).toList());
        return received.stream()
                .map(application -> ApplicationResponse.forTeam(application,
                        notes.getOrDefault(application.getId(), NoteSummary.NONE)))
                .toList();
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

    /** EXT-APP-02 — upcoming interviews of the recruiter's company. */
    @GetMapping("/interviews/upcoming")
    @PreAuthorize("hasRole('RECRUITER')")
    public List<UpcomingInterviewResponse> upcomingInterviews(@AuthenticationPrincipal AuthenticatedUser user) {
        return applicationService.upcomingInterviews(user.id()).stream().map(UpcomingInterviewResponse::from).toList();
    }

    /** BE-APP-04, with a message for the candidate (EXT-APP-01) and an optional interview (EXT-APP-02). */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ApplicationResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                            @Valid @RequestBody StatusChangeRequest request) {
        Application changed = applicationService.changeStatus(user.id(), id, request.status(), request.message(),
                request.interview());
        return ApplicationResponse.forTeam(changed, noteService.summaryOf(changed.getId()));
    }

    /** EXT-APP-02 — plans or reschedules the interview of an application in INTERVIEW. */
    @PutMapping("/{id}/interview")
    @PreAuthorize("hasRole('RECRUITER')")
    public ApplicationResponse scheduleInterview(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                                 @Valid @RequestBody InterviewRequest request) {
        Application scheduled = applicationService.scheduleInterview(user.id(), id, request);
        return ApplicationResponse.forTeam(scheduled, noteService.summaryOf(scheduled.getId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CANDIDATE')")
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        applicationService.withdraw(user.id(), id);
    }

    /** EXT-APP-03 — team notes, oldest first; never for the candidate. */
    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public List<NoteResponse> notes(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return noteService.list(user, id).stream().map(NoteResponse::from).toList();
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECRUITER')")
    public NoteResponse addNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                @Valid @RequestBody NoteRequest request) {
        return NoteResponse.from(noteService.add(user.id(), id, request));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RECRUITER')")
    public void deleteNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                           @PathVariable UUID noteId) {
        noteService.delete(user.id(), id, noteId);
    }
}
