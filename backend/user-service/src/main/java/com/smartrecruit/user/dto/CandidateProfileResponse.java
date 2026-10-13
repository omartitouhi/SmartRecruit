package com.smartrecruit.user.dto;

import com.smartrecruit.user.model.CandidateProfile;
import com.smartrecruit.user.model.Education;
import com.smartrecruit.user.model.Experience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public shape of {@link CandidateProfile}, as consumed by the frontend
 * (see {@code src/types/entities.ts} in {@code smartrecruit-frontend}).
 * Never exposes the internal {@code cvStorageKey}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfileResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;
    private List<String> skills;
    private List<Experience> experiences;
    private List<Education> education;
    private List<String> languages;
    private String cvUrl;
    private Instant cvParsedAt;
    private Instant createdAt;
    private Instant updatedAt;

    /** true when the candidate exercised the right to be forgotten: every personal field is then null. */
    private boolean deleted;

    /** Placeholder shown instead of a deleted profile (BE-USER-04): no personal data at all. */
    public static CandidateProfileResponse deletedAccount(UUID id) {
        return CandidateProfileResponse.builder().id(id).deleted(true).build();
    }

    public static CandidateProfileResponse from(CandidateProfile profile) {
        return CandidateProfileResponse.builder()
                .id(profile.getId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .location(profile.getLocation())
                .skills(List.copyOf(profile.getSkills()))
                .experiences(List.copyOf(profile.getExperiences()))
                .education(List.copyOf(profile.getEducation()))
                .languages(List.copyOf(profile.getLanguages()))
                .cvUrl(profile.getCvUrl())
                .cvParsedAt(profile.getCvParsedAt())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
