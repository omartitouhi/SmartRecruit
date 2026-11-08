package com.smartrecruit.job.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URIs of the services job-service calls directly (not through the gateway).
 */
@ConfigurationProperties(prefix = "services")
public record ServiceEndpointsProperties(String aiServiceUri, String userServiceUri, String applicationServiceUri) {
}
