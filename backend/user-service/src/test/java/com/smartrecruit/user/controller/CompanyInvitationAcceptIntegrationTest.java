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
 * BE-COMP-03 — POST /api/v1/users/companies/invitations/accept.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingInvitationNotifier.Config.class)
class CompanyInvitationAcceptIntegrationTest {

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
    private final UUID invitee = UUID.randomUUID();
    private UUID companyId;

    @BeforeEach
    void setUp() throws Exception {
        invitationRepository.deleteAll();
        companyRepository.deleteAll();
        notifier.sent().clear();
        companyId = companyRepository.save(Company.builder().id(UUID.randomUUID()).name("Acme").sector("IT")
                .members(new ArrayList<>(List.of(member))).build()).getId();
        mockMvc.perform(post("/api/v1/users/companies/" + companyId + "/invitations")
                        .with(as(verifier, member, "RECRUITER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"bob@example.com\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions accept(UUID account, String role, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/users/companies/invitations/accept")
                .with(as(verifier, account, role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"));
    }

    private String token() {
        return notifier.last().token();
    }

    @Test
    void invitedRecruiterJoinsTheCompany() throws Exception {
        accept(invitee, "RECRUITER", token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId.toString()))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[?(@ == '" + invitee + "')]").exists());

        assertThat(companyRepository.findByMember(invitee)).isPresent();
        assertThat(invitationRepository.findAll().get(0).getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        mockMvc.perform(get("/api/v1/users/companies/me").with(as(verifier, invitee, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId.toString()));
        mockMvc.perform(get("/api/v1/users/companies/" + companyId + "/invitations")
                        .with(as(verifier, invitee, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    void aTokenCanOnlyBeUsedOnce() throws Exception {
        String token = token();
        accept(invitee, "RECRUITER", token).andExpect(status().isOk());

        accept(UUID.randomUUID(), "RECRUITER", token).andExpect(status().isGone());
        mockMvc.perform(get("/api/v1/users/companies/me").with(as(verifier, member, "RECRUITER")))
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void anExpiredInvitationIsRefusedWith410() throws Exception {
        CompanyInvitation invitation = invitationRepository.findAll().get(0);
        invitation.setExpiresAt(Instant.now().minusSeconds(1));
        invitationRepository.save(invitation);

        accept(invitee, "RECRUITER", token())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
        assertThat(companyRepository.findByMember(invitee)).isEmpty();
    }

    @Test
    void anUnknownTokenIsRefusedWith400() throws Exception {
        accept(invitee, "RECRUITER", "not-a-real-token").andExpect(status().isBadRequest());
        accept(invitee, "RECRUITER", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.token").isNotEmpty());
        assertThat(companyRepository.findByMember(invitee)).isEmpty();
    }

    @Test
    void aRecruiterAlreadyInACompanyGets409AndKeepsTheInvitationUsable() throws Exception {
        UUID busy = UUID.randomUUID();
        companyRepository.save(Company.builder().id(UUID.randomUUID()).name("Globex").sector("Finance")
                .members(new ArrayList<>(List.of(busy))).build());

        accept(busy, "RECRUITER", token()).andExpect(status().isConflict());

        assertThat(invitationRepository.findAll().get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
        accept(invitee, "RECRUITER", token()).andExpect(status().isOk());
    }

    @Test
    void onlyRecruitersCanAccept() throws Exception {
        accept(invitee, "CANDIDATE", token()).andExpect(status().isForbidden());
        accept(invitee, "ADMIN", token()).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/companies/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(invitationRepository.findAll().get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
    }
}
