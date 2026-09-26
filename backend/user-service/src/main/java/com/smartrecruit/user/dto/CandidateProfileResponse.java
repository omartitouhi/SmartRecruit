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
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfileResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String phone;
    private String location;
    private List<String> skills;
    private List<Experience> experiences;
    private List<Education> education;
    private List<String> languages;
    private String cvUrl;
    private Instant cvParsedAt;

    public static CandidateProfileResponse from(CandidateProfile profile) {
        return CandidateProfileResponse.builder()
                .id(profile.getId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phone(profile.getPhone())
                .location(profile.getLocation())
                .skills(profile.getSkills())
                .experiences(profile.getExperiences())
                .education(profile.getEducation())
                .languages(profile.getLanguages())
                .cvUrl(profile.getCvUrl())
                .cvParsedAt(profile.getCvParsedAt())
                .build();
    }
}
