package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.CompanyInvitation;
import com.smartrecruit.user.model.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, UUID> {

    Optional<CompanyInvitation> findByTokenHash(String tokenHash);

    List<CompanyInvitation> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** True when a still valid invitation of this email to this company is waiting for an answer. */
    boolean existsByCompanyIdAndEmailAndStatusAndExpiresAtAfter(UUID companyId, String email,
                                                                InvitationStatus status, Instant now);

    /** Single-use guarantee: only one of two concurrent acceptances updates the row; returns rows updated. */
    @Modifying
    @Query("update CompanyInvitation i set i.status = com.smartrecruit.user.model.InvitationStatus.ACCEPTED "
            + "where i.id = :id and i.status = com.smartrecruit.user.model.InvitationStatus.PENDING")
    int markAccepted(@Param("id") UUID id);
}
