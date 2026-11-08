package com.smartrecruit.application.service;

import com.smartrecruit.application.dto.NoteRequest;
import com.smartrecruit.application.dto.NoteSummary;
import com.smartrecruit.application.exception.ApiException;
import com.smartrecruit.application.model.Application;
import com.smartrecruit.application.model.ApplicationNote;
import com.smartrecruit.application.repository.ApplicationNoteRepository;
import com.smartrecruit.application.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EXT-APP-03 — internal notes and ratings of the hiring team. Recruiters of the company owning the application
 * read and write them, admins only read them; the candidate never sees them.
 */
@Service
@RequiredArgsConstructor
public class ApplicationNoteService {

    private final ApplicationNoteRepository notes;
    private final ApplicationService applications;
    private final Clock clock;

    public List<ApplicationNote> list(AuthenticatedUser user, UUID applicationId) {
        Application application = applications.findApplication(applicationId);
        if (!"ADMIN".equals(user.role())) {
            applications.requireMemberOf(user.id(), application.getCompanyId());
        }
        return notes.findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }

    public ApplicationNote add(UUID recruiterId, UUID applicationId, NoteRequest request) {
        Application application = applications.findApplication(applicationId);
        applications.requireMemberOf(recruiterId, application.getCompanyId());
        return notes.save(ApplicationNote.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .authorId(recruiterId)
                .content(request.content().strip())
                .rating(request.rating())
                .createdAt(clock.instant())
                .build());
    }

    /** Only the author removes a note (403 otherwise); unknown application or note → 404. */
    public void delete(UUID recruiterId, UUID applicationId, UUID noteId) {
        applications.findApplication(applicationId);
        ApplicationNote note = notes.findById(noteId)
                .filter(found -> found.getApplicationId().equals(applicationId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Note introuvable"));
        if (!note.getAuthorId().equals(recruiterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Seul l'auteur peut supprimer cette note");
        }
        notes.delete(note);
    }

    /** Count and average rating (1 decimal) per application, in one query; absent applications have no notes. */
    public Map<UUID, NoteSummary> summaries(Collection<UUID> applicationIds) {
        Map<UUID, NoteSummary> summaries = new HashMap<>();
        if (applicationIds.isEmpty()) {
            return summaries;
        }
        for (Object[] row : notes.summarize(applicationIds)) {
            Double average = row[2] == null ? null : Math.round(((Number) row[2]).doubleValue() * 10) / 10.0;
            summaries.put((UUID) row[0], new NoteSummary(((Number) row[1]).longValue(), average));
        }
        return summaries;
    }

    public NoteSummary summaryOf(UUID applicationId) {
        return summaries(List.of(applicationId)).getOrDefault(applicationId, NoteSummary.NONE);
    }
}
