package com.smartrecruit.user.service;

import com.smartrecruit.user.dto.CandidateProfilePatchRequest;
import com.smartrecruit.user.dto.CandidateProfileRequest;
import com.smartrecruit.user.dto.CandidateProfileResponse;
import com.smartrecruit.user.dto.EducationRequest;
import com.smartrecruit.user.dto.ExperienceRequest;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.model.CandidateProfile;
import com.smartrecruit.user.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileRepository profileRepository;

    /** Result of a create-or-replace, so the controller can answer 201 or 200. */
    public record SaveResult(CandidateProfileResponse profile, boolean created) {
    }

    /**
     * Creates the profile of the given account or fully replaces it (BE-USER-01).
     * The profile id is always the account id carried by the access token.
     */
    @Transactional
    public SaveResult createOrReplace(UUID accountId, CandidateProfileRequest request) {
        CandidateProfile existing = profileRepository.findById(accountId).orElse(null);
        boolean created = existing == null;
        CandidateProfile profile = created ? CandidateProfile.builder().id(accountId).build() : existing;

        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());
        profile.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        profile.setPhone(blankToNull(request.phone()));
        profile.setLocation(blankToNull(request.location()));
        CandidateProfile.replace(profile.getSkills(), trimDistinct(request.skills()));
        CandidateProfile.replace(profile.getLanguages(), trimDistinct(request.languages()));
        CandidateProfile.replace(profile.getExperiences(),
                nullToEmpty(request.experiences()).stream().map(ExperienceRequest::toEntity).toList());
        CandidateProfile.replace(profile.getEducation(),
                nullToEmpty(request.education()).stream().map(EducationRequest::toEntity).toList());

        CandidateProfile saved = profileRepository.saveAndFlush(profile);
        return new SaveResult(CandidateProfileResponse.from(saved), created);
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse get(UUID accountId) {
        return CandidateProfileResponse.from(findOrThrow(accountId));
    }

    /**
     * Partial update (BE-USER-03): only the fields present in the request body are modified.
     * {@code updatedAt} is refreshed by the entity's {@code @PreUpdate} hook.
     */
    @Transactional
    public CandidateProfileResponse patch(UUID accountId, CandidateProfilePatchRequest request) {
        CandidateProfile profile = findOrThrow(accountId);

        if (request.has("firstName")) {
            profile.setFirstName(request.firstName().trim());
        }
        if (request.has("lastName")) {
            profile.setLastName(request.lastName().trim());
        }
        if (request.has("email")) {
            profile.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        }
        if (request.has("phone")) {
            profile.setPhone(blankToNull(request.phone()));
        }
        if (request.has("location")) {
            profile.setLocation(blankToNull(request.location()));
        }
        if (request.has("skills")) {
            CandidateProfile.replace(profile.getSkills(), trimDistinct(request.skills()));
        }
        if (request.has("languages")) {
            CandidateProfile.replace(profile.getLanguages(), trimDistinct(request.languages()));
        }
        if (request.has("experiences")) {
            CandidateProfile.replace(profile.getExperiences(),
                    nullToEmpty(request.experiences()).stream().map(ExperienceRequest::toEntity).toList());
        }
        if (request.has("education")) {
            CandidateProfile.replace(profile.getEducation(),
                    nullToEmpty(request.education()).stream().map(EducationRequest::toEntity).toList());
        }
        return CandidateProfileResponse.from(profileRepository.saveAndFlush(profile));
    }

    private CandidateProfile findOrThrow(UUID accountId) {
        return profileRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Profil candidat introuvable : créez-le d'abord avec PUT /api/v1/users/candidates/me"));
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static List<String> trimDistinct(List<String> values) {
        return nullToEmpty(values).stream().map(String::trim).distinct().toList();
    }

    static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
