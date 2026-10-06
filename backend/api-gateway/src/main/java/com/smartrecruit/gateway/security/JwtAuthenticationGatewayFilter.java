package com.smartrecruit.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Validates the access JWT once for the whole platform (BE-SEC-04):
 * <ul>
 *     <li>public paths and CORS preflights go through untouched;</li>
 *     <li>any other {@code /api/**} request without a valid token is answered 401 here and never
 *     reaches a microservice;</li>
 *     <li>for a valid token, the identity (id, role) is forwarded in signed internal headers.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationGatewayFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> ROLES = Set.of("CANDIDATE", "RECRUITER", "ADMIN");

    private final SecretKey jwtKey;
    private final String internalSecret;
    private final Set<String> publicPaths;
    private final Clock clock;

    public JwtAuthenticationGatewayFilter(GatewaySecurityProperties properties, Clock clock) {
        this.jwtKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.internalSecret = properties.internalAuth().secret();
        this.publicPaths = Set.copyOf(properties.publicPaths());
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isPublic = publicPaths.contains(request.getRequestURI())
                || HttpMethod.OPTIONS.matches(request.getMethod());
        Optional<Identity> identity = authenticate(request);

        if (identity.isEmpty() && !isPublic) {
            GatewayErrorWriter.write(response, request, HttpStatus.UNAUTHORIZED,
                    "Authentification requise : token absent, invalide ou expiré");
            return;
        }
        Map<String, String> headers = identity.map(this::signedHeaders).orElse(Map.of());
        chain.doFilter(new IdentityRequestWrapper(request, headers), response);
    }

    private Optional<Identity> authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(jwtKey).build()
                    .parseSignedClaims(header.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();
            String role = claims.get("role", String.class);
            if (claims.getSubject() == null || !ROLES.contains(role)) {
                return Optional.empty();
            }
            return Optional.of(new Identity(UUID.fromString(claims.getSubject()).toString(), role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Map<String, String> signedHeaders(Identity identity) {
        long timestamp = clock.instant().getEpochSecond();
        return Map.of(
                InternalIdentityHeaders.USER_ID, identity.userId(),
                InternalIdentityHeaders.USER_ROLE, identity.role(),
                InternalIdentityHeaders.TIMESTAMP, Long.toString(timestamp),
                InternalIdentityHeaders.SIGNATURE,
                InternalIdentityHeaders.sign(internalSecret, identity.userId(), identity.role(), timestamp));
    }

    private record Identity(String userId, String role) {
    }
}
