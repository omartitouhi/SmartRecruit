package com.smartrecruit.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Static routing table: one route per backend microservice.
 * No service discovery (Eureka) at this project scale — hostnames are
 * resolved via Docker Compose service names / env vars.
 */
@Configuration
public class GatewayConfig {

    @Value("${services.auth-service.uri}")
    private String authServiceUri;

    @Value("${services.user-service.uri}")
    private String userServiceUri;

    @Value("${services.job-service.uri}")
    private String jobServiceUri;

    @Value("${services.application-service.uri}")
    private String applicationServiceUri;

    @Value("${services.dashboard-service.uri}")
    private String dashboardServiceUri;

    @Value("${services.ai-service.uri}")
    private String aiServiceUri;

    @Bean
    public RouterFunction<ServerResponse> authRoute() {
        return route("auth_service")
                .route(path("/api/v1/auth/**"), http())
                .before(uri(authServiceUri))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> userRoute() {
        return route("user_service")
                .route(path("/api/v1/users/**"), http())
                .before(uri(userServiceUri))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> jobRoute() {
        return route("job_service")
                .route(path("/api/v1/jobs/**"), http())
                .before(uri(jobServiceUri))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> applicationRoute() {
        return route("application_service")
                .route(path("/api/v1/applications/**"), http())
                .before(uri(applicationServiceUri))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> dashboardRoute() {
        return route("dashboard_service")
                .route(path("/api/v1/dashboard/**"), http())
                .before(uri(dashboardServiceUri))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> aiRoute() {
        return route("ai_service")
                .route(path("/api/v1/ai/**"), http())
                .before(uri(aiServiceUri))
                .build();
    }
}
