package com.smartrecruit.user.service;

import com.smartrecruit.user.dto.CompanyResponse;
import com.smartrecruit.user.dto.InvitationRequest;
import com.smartrecruit.user.dto.InvitationResponse;
import com.smartrecruit.user.exception.ApiException;
import com.smartrecruit.user.model.Company;
import com.smartrecruit.user.model.CompanyInvitation;
import com.smartrecruit.user.model.InvitationStatus;
import com.smartrecruit.user.repository.CompanyInvitationRepository;
import com.smartrecruit.user.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Invitations of recruiters to a company (BE-COMP-03). Only members can invite and list; the
 * invitation token is random, stored hashed and only ever sent through the notifier.
 */
@Service
@RequiredArgsConstructor
public class CompanyInvitationService {

    private final CompanyInvitationRepository invitationRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final CompanyInvitationNotifier notifier;
    private final CompanyInvitationProperties properties;
    private final Clock clock;

    /** Creates a PENDING invitation and notifies the invitee; 409 if one is already pending for this email. */
    @Transactional
    public InvitationResponse invite(UUID accountId, UUID companyId, InvitationRequest request) {
        Company company = companyService.findForMember(accountId, companyId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        if (invitationRepository.existsByCompanyIdAndEmailAndStatusAndExpiresAtAfter(
                companyId, email, InvitationStatus.PENDING, now)) {
            throw new ApiException(HttpStatus.CONFLICT, "Une invitation est déjà en attente pour cet email");
        }

        String token = InvitationTokens.generate();
        CompanyInvitation invitation = invitationRepository.saveAndFlush(CompanyInvitation.builder()
                .companyId(companyId)
                .email(email)
                .tokenHash(InvitationTokens.hash(token))
                .status(InvitationStatus.PENDING)
                .expiresAt(now.plus(properties.tokenTtl()))
                .build());

        String link = UriComponentsBuilder.fromUriString(properties.linkBaseUrl())
                .queryParam("token", token)
                .toUriString();
        notifier.sendInvitation(email, company.getName(), link);
        return InvitationResponse.from(invitation, now);
    }

    /**
     * Attaches the connected recruiter to the inviting company. 400 for an unknown token, 410 for an
     * expired or already used one, 409 if the recruiter already belongs to a company. The token alone
     * proves the invitation: recruiter emails are not known to user-service, so it is not compared.
     */
    @Transactional
    public CompanyResponse accept(UUID accountId, String token) {
        CompanyInvitation invitation = invitationRepository.findByTokenHash(InvitationTokens.hash(token.trim()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Jeton d'invitation invalide"));
        switch (invitation.statusAt(clock.instant())) {
            case ACCEPTED -> throw new ApiException(HttpStatus.GONE, "Cette invitation a déjà été utilisée");
            case EXPIRED -> throw new ApiException(HttpStatus.GONE, "Cette invitation a expiré");
            default -> { }
        }
        if (companyRepository.findByMember(accountId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, CompanyService.ALREADY_IN_COMPANY);
        }
        Company company = companyRepository.findById(invitation.getCompanyId())
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "L'entreprise de cette invitation n'existe plus"));
        if (invitationRepository.markAccepted(invitation.getId()) == 0) {
            throw new ApiException(HttpStatus.GONE, "Cette invitation a déjà été utilisée");
        }
        company.getMembers().add(accountId);
        try {
            return CompanyResponse.from(companyRepository.saveAndFlush(company));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, CompanyService.ALREADY_IN_COMPANY);
        }
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list(UUID accountId, UUID companyId) {
        companyService.findForMember(accountId, companyId);
        Instant now = clock.instant();
        return invitationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(invitation -> InvitationResponse.from(invitation, now))
                .toList();
    }
}
