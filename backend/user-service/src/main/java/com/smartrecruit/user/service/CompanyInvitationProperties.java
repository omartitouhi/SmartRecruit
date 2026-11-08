package com.smartrecruit.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of company invitations (BE-COMP-03).
 *
 * @param tokenTtl    validity of an invitation (7 days)
 * @param linkBaseUrl frontend page that receives the {@code token} query parameter
 */
@ConfigurationProperties(prefix = "company-invitation")
public record CompanyInvitationProperties(Duration tokenTtl, String linkBaseUrl) {
}
