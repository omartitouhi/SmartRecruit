package com.smartrecruit.job.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One JDK HTTP server playing user-service, application-service and ai-service at once: responses
 * are keyed by exact path (404 when none is set) and every call is recorded. Shared by the whole
 * test JVM so Spring contexts that point at it can be cached.
 */
public final class FakeBackends {

    public record Call(String method, String path, String query, String role, String body, boolean h2cUpgrade) {
    }

    private record Response(int status, String body) {
    }

    public static final FakeBackends INSTANCE = new FakeBackends();

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    private FakeBackends() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    public String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void reset() {
        responses.clear();
        calls.clear();
    }

    public void respond(String path, int status, String body) {
        responses.put(path, new Response(status, body));
    }

    public List<Call> callsTo(String path) {
        return calls.stream().filter(call -> call.path().equals(path)).toList();
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean upgrade = exchange.getRequestHeaders().containsKey("Upgrade")
                || exchange.getRequestHeaders().containsKey("HTTP2-Settings");
        calls.add(new Call(exchange.getRequestMethod(), path, exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders().getFirst("X-Auth-User-Role"), requestBody, upgrade));
        Response response = responses.getOrDefault(path, new Response(404, ""));
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
