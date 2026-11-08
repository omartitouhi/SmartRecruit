package com.smartrecruit.application.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URIs of the services application-service calls directly (not through the gateway).
 */
@ConfigurationProperties(prefix = "services")
public record ServiceEndpointsProperties(String jobServiceUri, String userServiceUri, String aiServiceUri,
                                         String dashboardServiceUri) {
}
