package com.smartrecruit.auth.repository;

import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByEmail(String email);

    Optional<Account> findByEmail(String email);

    /** Admin listing; a {@code null} filter matches everything. */
    @Query("select a from Account a where (:role is null or a.role = :role) "
            + "and (:status is null or a.status = :status)")
    Page<Account> search(@Param("role") Role role, @Param("status") AccountStatus status, Pageable pageable);
}
