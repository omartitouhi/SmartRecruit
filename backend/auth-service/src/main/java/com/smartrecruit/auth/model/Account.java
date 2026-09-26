package com.smartrecruit.auth.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Field-for-field match of the {@code Account} entity in {@code DATA_MODEL.md} (§3.1).
 * {@code passwordHash} must never be serialized in an API response — see
 * {@link com.smartrecruit.auth.dto.AccountResponse}.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    private UUID id;

    private String email;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
