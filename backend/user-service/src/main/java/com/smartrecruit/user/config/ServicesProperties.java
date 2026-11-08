package com.smartrecruit.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URIs of the other services user-service calls (API_CONTRACTS.md section 0).
 *
 * @param dashboardServiceUri base URI of dashboard-service, target of the audit events
 */
@ConfigurationProperties(prefix = "services")
public record ServicesProperties(String dashboardServiceUri) {
}
