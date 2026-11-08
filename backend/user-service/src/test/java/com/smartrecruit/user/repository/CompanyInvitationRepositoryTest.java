package com.smartrecruit.user.repository;

import com.smartrecruit.user.model.CompanyInvitation;
import com.smartrecruit.user.model.InvitationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BE-COMP-03 — invitation persistence, expiry derivation and single-use acceptance. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CompanyInvitationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

    @Autowired
    private CompanyInvitationRepository repository;

    private CompanyInvitation invitation(UUID companyId, String email, String hash, Instant expiresAt) {
        return repository.saveAndFlush(CompanyInvitation.builder().companyId(companyId).email(email)
                .tokenHash(hash).status(InvitationStatus.PENDING).expiresAt(expiresAt).build());
    }

    @Test
    void aPendingInvitationPastItsExpiryIsReportedExpired() {
        CompanyInvitation live = invitation(UUID.randomUUID(), "a@x.test", "h1", NOW.plus(Duration.ofDays(7)));
        CompanyInvitation old = invitation(UUID.randomUUID(), "b@x.test", "h2", NOW.minusSeconds(1));

        assertThat(live.statusAt(NOW)).isEqualTo(InvitationStatus.PENDING);
        assertThat(old.statusAt(NOW)).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(old.statusAt(NOW.minusSeconds(2))).isEqualTo(InvitationStatus.PENDING);
        live.setStatus(InvitationStatus.ACCEPTED);
        assertThat(live.statusAt(NOW.plus(Duration.ofDays(30)))).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void duplicateCheckIgnoresExpiredAndAcceptedInvitations() {
        UUID company = UUID.randomUUID();
        invitation(company, "old@x.test", "h1", NOW.minusSeconds(1));
        CompanyInvitation accepted = invitation(company, "done@x.test", "h2", NOW.plusSeconds(60));
        accepted.setStatus(InvitationStatus.ACCEPTED);
        repository.saveAndFlush(accepted);
        invitation(company, "new@x.test", "h3", NOW.plusSeconds(60));

        assertThat(repository.existsByCompanyIdAndEmailAndStatusAndExpiresAtAfter(
                company, "new@x.test", InvitationStatus.PENDING, NOW)).isTrue();
        assertThat(repository.existsByCompanyIdAndEmailAndStatusAndExpiresAtAfter(
                company, "old@x.test", InvitationStatus.PENDING, NOW)).isFalse();
        assertThat(repository.existsByCompanyIdAndEmailAndStatusAndExpiresAtAfter(
                company, "done@x.test", InvitationStatus.PENDING, NOW)).isFalse();
        assertThat(repository.existsByCompanyIdAndEmailAndStatusAndExpiresAtAfter(
                UUID.randomUUID(), "new@x.test", InvitationStatus.PENDING, NOW)).isFalse();
    }

    @Test
    void markAcceptedWorksOnlyOnce() {
        CompanyInvitation saved = invitation(UUID.randomUUID(), "a@x.test", "h1", NOW.plusSeconds(60));

        assertThat(repository.markAccepted(saved.getId())).isEqualTo(1);
        assertThat(repository.markAccepted(saved.getId())).isZero();
    }

    @Test
    void tokenHashIsUniqueAndSearchable() {
        CompanyInvitation saved = invitation(UUID.randomUUID(), "a@x.test", "same", NOW.plusSeconds(60));

        assertThat(repository.findByTokenHash("same")).get().extracting(CompanyInvitation::getId).isEqualTo(saved.getId());
        assertThat(repository.findByTokenHash("other")).isEmpty();
        assertThatThrownBy(() -> invitation(UUID.randomUUID(), "b@x.test", "same", NOW.plusSeconds(60)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
