package com.smartrecruit.job.config;

import com.smartrecruit.job.security.GatewayIdentityFilter;
import com.smartrecruit.job.security.SecurityErrorHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * RBAC (BE-AUTH-04): every endpoint requires an identity validated by the api-gateway (BE-SEC-04)
 * except the public ones below.
 * Role restrictions are declared on controllers with {@code @PreAuthorize("hasRole('RECRUITER')")}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayIdentityFilter gatewayIdentityFilter,
                                                   SecurityErrorHandler securityErrorHandler) throws Exception {
        http
                // CSRF does not apply (BE-SEC-03): the identity travels in headers set by the gateway from
                // the Authorization header, never in a cookie, and no session is created.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // BE-SEC-03: JSON-only API, nothing may be rendered or framed from its responses.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/ping", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )
                .addFilterBefore(gatewayIdentityFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** The filter is wired into the security chain only, not registered a second time as a servlet filter. */
    @Bean
    public FilterRegistrationBean<GatewayIdentityFilter> gatewayIdentityFilterRegistration(GatewayIdentityFilter filter) {
        FilterRegistrationBean<GatewayIdentityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
