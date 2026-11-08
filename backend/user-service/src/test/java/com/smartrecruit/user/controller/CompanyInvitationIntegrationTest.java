package com.smartrecruit.user.controller;

import com.smartrecruit.user.model.Company;
import com.smartrecruit.user.model.CompanyInvitation;
import com.smartrecruit.user.model.InvitationStatus;
import com.smartrecruit.user.repository.CompanyInvitationRepository;
import com.smartrecruit.user.repository.CompanyRepository;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import com.smartrecruit.user.support.CapturingInvitationNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.smartrecruit.user.support.GatewayIdentity.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-COMP-03 — POST and GET /api/v1/users/companies/{id}/invitations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingInvitationNotifier.Config.class)
class CompanyInvitationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalIdentityVerifier verifier;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyInvitationRepository invitationRepository;

    @Autowired
    private CapturingInvitationNotifier notifier;

    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private UUID companyId;

    @BeforeEach
    void setUp() {
        invitationRepository.deleteAll();
        companyRepository.deleteAll();
        notifier.sent().clear();
        companyId = companyRepository.save(Company.builder().id(UUID.randomUUID()).name("Acme").sector("IT")
                .members(new ArrayList<>(List.of(member))).build()).getId();
    }

    private ResultActions invite(UUID account, String role, UUID company, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/users/companies/" + company + "/invitations")
                .with(as(verifier, account, role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}"));
    }

    @Test
    void memberCreatesAPendingInvitationExpiringInSevenDays() throws Exception {
        Instant before = Instant.now();

        invite(member, "RECRUITER", companyId, "Bob@Example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist());

        CompanyInvitation stored = invitationRepository.findAll().get(0);
        assertThat(stored.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(stored.getCompanyId()).isEqualTo(companyId);
        assertThat(stored.getExpiresAt()).isBetween(before.plus(Duration.ofDays(7)).minusSeconds(5),
                Instant.now().plus(Duration.ofDays(7)).plusSeconds(5));
    }

    @Test
    void tokenIsStoredHashedAndOnlyDeliveredByTheNotifier() throws Exception {
        invite(member, "RECRUITER", companyId, "bob@example.com").andExpect(status().isCreated());

        CapturingInvitationNotifier.Sent sent = notifier.last();
        assertThat(sent.email()).isEqualTo("bob@example.com");
        assertThat(sent.companyName()).isEqualTo("Acme");
        assertThat(sent.link()).startsWith("http://frontend.test/invitations/accept?token=");
        String stored = invitationRepository.findAll().get(0).getTokenHash();
        assertThat(stored).matches("[0-9a-f]{64}").isNotEqualTo(sent.token());
    }

    @Test
    void aSecondPendingInvitationForTheSameEmailIsRejectedUntilItExpires() throws Exception {
        invite(member, "RECRUITER", companyId, "bob@example.com").andExpect(status().isCreated());

        invite(member, "RECRUITER", companyId, "BOB@example.com").andExpect(status().isConflict());
        invite(member, "RECRUITER", companyId, "carol@example.com").andExpect(status().isCreated());
        assertThat(invitationRepository.count()).isEqualTo(2);

        CompanyInvitation bob = invitationRepository.findAll().stream()
                .filter(i -> i.getEmail().equals("bob@example.com")).findFirst().orElseThrow();
        bob.setExpiresAt(Instant.now().minusSeconds(1));
        invitationRepository.save(bob);
        invite(member, "RECRUITER", companyId, "bob@example.com").andExpect(status().isCreated());
    }

    @Test
    void invalidEmailIsReportedWithFieldErrors() throws Exception {
        invite(member, "RECRUITER", companyId, "not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        invite(member, "RECRUITER", companyId, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        assertThat(invitationRepository.count()).isZero();
    }

    @Test
    void onlyRecruiterMembersCanInvite() throws Exception {
        invite(outsider, "RECRUITER", companyId, "bob@example.com").andExpect(status().isForbidden());
        invite(member, "CANDIDATE", companyId, "bob@example.com").andExpect(status().isForbidden());
        invite(member, "ADMIN", companyId, "bob@example.com").andExpect(status().isForbidden());
        invite(member, "RECRUITER", UUID.randomUUID(), "bob@example.com").andExpect(status().isNotFound());
        assertThat(invitationRepository.count()).isZero();
        assertThat(notifier.sent()).isEmpty();
    }

    @Test
    void listShowsInvitationsWithTheirEffectiveStatus() throws Exception {
        invite(member, "RECRUITER", companyId, "bob@example.com").andExpect(status().isCreated());
        invite(member, "RECRUITER", companyId, "old@example.com").andExpect(status().isCreated());
        CompanyInvitation old = invitationRepository.findAll().stream()
                .filter(i -> i.getEmail().equals("old@example.com")).findFirst().orElseThrow();
        old.setExpiresAt(Instant.now().minusSeconds(1));
        invitationRepository.save(old);

        mockMvc.perform(get("/api/v1/users/companies/" + companyId + "/invitations")
                        .with(as(verifier, member, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email == 'bob@example.com')].status").value("PENDING"))
                .andExpect(jsonPath("$[?(@.email == 'old@example.com')].status").value("EXPIRED"))
                .andExpect(jsonPath("$[0].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void listIsReservedToMembers() throws Exception {
        String path = "/api/v1/users/companies/" + companyId + "/invitations";

        mockMvc.perform(get(path).with(as(verifier, outsider, "RECRUITER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(path).with(as(verifier, member, "CANDIDATE"))).andExpect(status().isForbidden());
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/companies/" + UUID.randomUUID() + "/invitations")
                        .with(as(verifier, member, "RECRUITER")))
                .andExpect(status().isNotFound());
    }
}
