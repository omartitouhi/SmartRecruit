package com.smartrecruit.user.service;

/**
 * Delivers an invitation link to the invited recruiter. Sending real emails is out of the MVP scope,
 * so the default implementation only logs the link (same approach as auth-service's
 * {@code PasswordResetNotifier}).
 */
public interface CompanyInvitationNotifier {

    void sendInvitation(String email, String companyName, String acceptLink);
}
