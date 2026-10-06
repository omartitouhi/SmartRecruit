package com.smartrecruit.auth.config;

import com.smartrecruit.auth.security.GatewayIdentityFilter;
import com.smartrecruit.auth.security.SecurityErrorHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayIdentityFilter gatewayIdentityFilter,
                                                   SecurityErrorHandler securityErrorHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/ping", "/actuator/**", "/error",
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
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
