package com.smartrecruit.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param jwt          secret shared with auth-service to verify access tokens
 * @param internalAuth secret used to sign the identity headers sent to the microservices
 * @param publicPaths  exact paths reachable without a token
 */
@ConfigurationProperties(prefix = "security")
public record GatewaySecurityProperties(Secret jwt, Secret internalAuth, List<String> publicPaths) {

    public record Secret(String secret) {
    }
}
