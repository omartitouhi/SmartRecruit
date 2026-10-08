package com.smartrecruit.user.controller;

import com.smartrecruit.user.dto.CandidateProfileRequest;
import com.smartrecruit.user.dto.CandidateProfileResponse;
import com.smartrecruit.user.security.AuthenticatedUser;
import com.smartrecruit.user.service.CandidateProfilePatchParser;
import com.smartrecruit.user.service.CandidateProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Profile of the connected candidate. The profile id is the account id from the access token,
 * so a candidate can only ever read or write their own profile.
 */
@RestController
@RequestMapping("/api/v1/users/candidates/me")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class CandidateProfileController {

    private final CandidateProfileService profileService;
    private final CandidateProfilePatchParser patchParser;

    /** Current profile of the connected candidate — BE-USER-03. */
    @GetMapping
    public CandidateProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.get(user.id());
    }

    /** Partial update (JSON Merge Patch): only the fields present in the body change — BE-USER-03. */
    @PatchMapping
    public CandidateProfileResponse patch(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestBody JsonNode body) {
        return profileService.patch(user.id(), patchParser.parse(body));
    }

    /** Creates (201) or fully replaces (200) the profile — BE-USER-01. */
    @PutMapping
    public ResponseEntity<CandidateProfileResponse> createOrReplace(@AuthenticationPrincipal AuthenticatedUser user,
                                                                    @Valid @RequestBody CandidateProfileRequest request) {
        CandidateProfileService.SaveResult result = profileService.createOrReplace(user.id(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.profile());
    }
}
