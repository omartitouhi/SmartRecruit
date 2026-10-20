package com.smartrecruit.auth.service;

import com.smartrecruit.auth.dto.AccountResponse;
import com.smartrecruit.auth.dto.PageResponse;
import com.smartrecruit.auth.exception.ApiException;
import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import com.smartrecruit.auth.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Account management on behalf of the dashboard-service admin screens (BE-ADMIN-01). */
@Service
@RequiredArgsConstructor
public class AccountAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(Role role, AccountStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("createdAt").descending().and(Sort.by("id")));
        return PageResponse.of(accountRepository.search(role, status, pageable).map(AccountResponse::from));
    }

    /** Disabling also ends every open session; an erased account can never come back. */
    @Transactional
    public AccountResponse updateStatus(UUID accountId, AccountStatus status) {
        if (status == AccountStatus.DELETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le statut doit être ACTIVE ou DISABLED");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Compte introuvable"));
        if (account.getStatus() == AccountStatus.DELETED) {
            throw new ApiException(HttpStatus.CONFLICT, "Un compte supprimé ne peut pas être modifié");
        }
        account.setStatus(status);
        if (status == AccountStatus.DISABLED) {
            refreshTokenService.revokeAll(accountId);
        }
        return AccountResponse.from(account);
    }
}
