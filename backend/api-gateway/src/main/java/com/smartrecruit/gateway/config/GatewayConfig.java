package com.smartrecruit.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.http.HttpClient;
import java.time.Duration;

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

    /**
     * Proxy to the backends over plain HTTP/1.1. The JDK client's default cleartext h2c upgrade
     * (Upgrade/HTTP2-Settings headers) breaks uvicorn on requests with a body. A stopped backend fails
     * within the connect timeout instead of holding the client until the OS gives up (reliability test).
     */
    @Bean
    public ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> http11ProxyClient(
            @Value("${gateway.connect-timeout:3s}") Duration connectTimeout) {
        return builder -> builder.withHttpClientCustomizer(
                client -> client.version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout));
    }

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
