package com.smartrecruit.auth.dto;

import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Public shape of {@link Account} — never exposes {@code passwordHash}.
 * JSON field names must stay in camelCase per {@code DATA_MODEL.md} §1.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {

    private UUID id;
    private String email;
    private Role role;
    private AccountStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .role(account.getRole())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
