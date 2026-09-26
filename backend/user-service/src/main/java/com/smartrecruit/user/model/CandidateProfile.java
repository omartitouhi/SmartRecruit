package com.smartrecruit.user.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Field-for-field match of the {@code CandidateProfile} entity in
 * {@code DATA_MODEL.md} (§3.2). {@code id} equals the {@code accountId} from
 * auth-service (1:1 relationship) — never generate a separate id here.
 */
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfile {

    @Id
    private UUID id;

    private String firstName;
    private String lastName;
    private String phone;

    /** Ville, utilisée pour le filtre de recherche d'offres (cf. DATA_MODEL.md §3.2). */
    private String location;

    @ElementCollection
    @CollectionTable(name = "candidate_skills", joinColumns = @JoinColumn(name = "candidate_id"))
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "candidate_experiences", joinColumns = @JoinColumn(name = "candidate_id"))
    @Builder.Default
    private List<Experience> experiences = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "candidate_education", joinColumns = @JoinColumn(name = "candidate_id"))
    @Builder.Default
    private List<Education> education = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "candidate_languages", joinColumns = @JoinColumn(name = "candidate_id"))
    @Builder.Default
    private List<String> languages = new ArrayList<>();

    /** Référence au fichier stocké (chiffré, cf. BE-SEC-01) — jamais l'URL brute du stockage. */
    private String cvUrl;

    /** Horodatage de la dernière analyse IA réussie (AI-CV-02). */
    private Instant cvParsedAt;
}
