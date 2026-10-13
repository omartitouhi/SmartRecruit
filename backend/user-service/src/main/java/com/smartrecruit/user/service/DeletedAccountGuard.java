package com.smartrecruit.user.service;

import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.repository.DeletedCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Prevents a deleted candidate's data from being recreated with a still-valid access token
 * (BE-USER-04): writes are answered 410 Gone.
 */
@Component
@RequiredArgsConstructor
public class DeletedAccountGuard {

    private final DeletedCandidateRepository deletedCandidateRepository;

    public boolean isDeleted(UUID accountId) {
        return deletedCandidateRepository.existsById(accountId);
    }

    public void ensureNotDeleted(UUID accountId) {
        if (isDeleted(accountId)) {
            throw new ApiException(HttpStatus.GONE, "Ce compte a été supprimé");
        }
    }
}
