package com.smartrecruit.application.dto;

import com.smartrecruit.application.model.ApplicationNote;

import java.time.Instant;
import java.util.UUID;

/** {@code ApplicationNote} of {@code API_CONTRACTS.md} §7.4 (EXT-APP-03). */
public record NoteResponse(UUID id, UUID applicationId, UUID authorId, String content, Integer rating, Instant createdAt) {

    public static NoteResponse from(ApplicationNote note) {
        return new NoteResponse(note.getId(), note.getApplicationId(), note.getAuthorId(), note.getContent(),
                note.getRating(), note.getCreatedAt());
    }
}
