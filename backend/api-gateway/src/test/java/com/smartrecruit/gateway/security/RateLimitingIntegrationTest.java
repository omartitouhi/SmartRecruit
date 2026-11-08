package com.smartrecruit.gateway.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-SEC-05 end to end: once the limit is reached, login attempts get 429 from the gateway and
 * never reach auth-service (played here by a JDK HTTP server).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"security.rate-limit.max-requests=3", "security.rate-limit.window=1m"})
class RateLimitingIntegrationTest {

    private static final HttpServer AUTH_SERVICE;
    private static final AtomicInteger HITS = new AtomicInteger();

    static {
        try {
            AUTH_SERVICE = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        AUTH_SERVICE.createContext("/", exchange -> {
            HITS.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        AUTH_SERVICE.start();
    }

    @DynamicPropertySource
    static void routeAuthToStub(DynamicPropertyRegistry registry) {
        registry.add("services.auth-service.uri", () -> "http://localhost:" + AUTH_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        AUTH_SERVICE.stop(0);
    }

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"a@b.c\",\"password\":\"wrong-password\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void loginAttemptsAboveTheLimitAreRejectedWith429() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login().statusCode()).isEqualTo(401);
        }

        HttpResponse<String> blocked = login();

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
        assertThat(blocked.body()).contains("\"status\":429");
        assertThat(HITS.get()).isEqualTo(3);
    }
}
