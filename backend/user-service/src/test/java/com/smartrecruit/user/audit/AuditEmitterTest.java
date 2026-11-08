package com.smartrecruit.user.audit;

import com.smartrecruit.user.config.ServicesProperties;
import com.smartrecruit.user.security.InternalIdentityVerifier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Audit events towards dashboard-service: payload, identity, HTTP/1.1 and best-effort behaviour. */
class AuditEmitterTest {

    private HttpServer dashboard;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile int code = 201;
    private final InternalIdentityVerifier signer = new InternalIdentityVerifier("test-secret", Clock.systemUTC());
    private final UUID actor = UUID.randomUUID();
    private final UUID candidate = UUID.randomUUID();

    @BeforeEach
    void startFakeDashboard() throws IOException {
        dashboard = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        dashboard.createContext("/internal/v1/audit-events", this::handle);
        dashboard.start();
    }

    @AfterEach
    void stopFakeDashboard() {
        dashboard.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            boolean h2c = exchange.getRequestHeaders().containsKey("Upgrade");
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Content-Type")
                    + " " + exchange.getRequestHeaders().getFirst(InternalIdentityVerifier.USER_ROLE) + (h2c ? " h2c" : "")
                    + "\n" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private AuditEmitter emitter(String uri) {
        return new AuditEmitter(new ServicesProperties(uri), signer);
    }

    private AuditEmitter emitterToFakeDashboard() {
        return emitter("http://localhost:" + dashboard.getAddress().getPort());
    }

    @Test
    void postsTheEventAsJsonWithTheServiceIdentity() {
        emitterToFakeDashboard().emit(actor, AuditAction.CV_VIEWED, candidate);

        assertThat(requests).hasSize(1);
        String request = requests.get(0);
        assertThat(request).startsWith("POST application/json SERVICE\n");
        assertThat(request).doesNotContain("h2c");
        assertThat(request).contains("\"actorId\":\"" + actor + "\"", "\"action\":\"CV_VIEWED\"",
                "\"resourceType\":\"CANDIDATE\"", "\"resourceId\":\"" + candidate + "\"");
    }

    @Test
    void profileViewsUseTheirOwnAction() {
        emitterToFakeDashboard().emit(actor, AuditAction.PROFILE_VIEWED, candidate);

        assertThat(requests.get(0)).contains("\"action\":\"PROFILE_VIEWED\"");
    }

    @Test
    void anErrorAnswerNeverThrows() {
        code = 500;

        assertThatCode(() -> emitterToFakeDashboard().emit(actor, AuditAction.CV_VIEWED, candidate))
                .doesNotThrowAnyException();
        assertThat(requests).hasSize(1);
    }

    @Test
    void anUnreachableDashboardNeverThrows() {
        assertThatCode(() -> emitter("http://localhost:1").emit(actor, AuditAction.CV_VIEWED, candidate))
                .doesNotThrowAnyException();
    }
}
