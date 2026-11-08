package com.smartrecruit.application.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Turns the signed identity headers set by the api-gateway into an authenticated principal with a
 * {@code ROLE_<role>} authority. The JWT itself is validated only once, by the gateway (BE-SEC-04).
 * Requests without a valid identity stay anonymous and are rejected with 401 by
 * {@link SecurityErrorHandler} when they reach a protected endpoint.
 */
@Component
@RequiredArgsConstructor
public class GatewayIdentityFilter extends OncePerRequestFilter {

    private final InternalIdentityVerifier identityVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        identityVerifier.verify(request).ifPresent(user -> {
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
        chain.doFilter(request, response);
    }
}
