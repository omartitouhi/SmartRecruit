package com.smartrecruit.auth.service;

/**
 * Delivers the password reset link to the user. Sending real emails is out of the MVP scope
 * (BE-AUTH-06), so the default implementation only logs the link.
 */
public interface PasswordResetNotifier {

    void sendResetLink(String email, String resetLink);
}
