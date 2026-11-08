package com.smartrecruit.dashboard.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URIs of the services dashboard-service calls directly (not through the gateway). */
@ConfigurationProperties(prefix = "services")
public record ServiceEndpointsProperties(String authServiceUri, String userServiceUri, String jobServiceUri,
                                         String applicationServiceUri) {
}
