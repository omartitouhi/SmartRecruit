package com.smartrecruit.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal note of the hiring team on an application, with an optional 1..5 rating (EXT-APP-03).
 * Never exposed to the candidate.
 */
@Entity
@Table(name = "application_notes", indexes = @Index(name = "idx_application_notes_application", columnList = "applicationId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationNote {

    @Id
    private UUID id;

    private UUID applicationId;

    /** accountId of the recruiter who wrote the note. */
    private UUID authorId;

    @Column(length = 2000)
    private String content;

    /** 1..5, null when the note carries no rating. */
    private Integer rating;

    private Instant createdAt;
}
