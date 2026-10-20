package com.smartrecruit.dashboard.support;

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
 * One JDK HTTP server standing in for auth, user, job and application services (their paths do not
 * overlap). Unregistered routes answer 404.
 */
public final class FakeServices {

    public record Call(String method, String target, String role, String body, boolean h2cUpgrade) {
    }

    private record Response(int status, String body) {
    }

    public static final FakeServices INSTANCE = new FakeServices();

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    private FakeServices() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void reset() {
        responses.clear();
        calls.clear();
    }

    /** @param path the URI path without query string */
    public void respond(String method, String path, int status, String body) {
        responses.put(method + " " + path, new Response(status, body));
    }

    public List<Call> calls() {
        return calls;
    }

    public List<Call> callsTo(String method, String path) {
        return calls.stream()
                .filter(c -> c.method().equals(method) && c.target().split("\\?")[0].equals(path))
                .toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean upgrade = exchange.getRequestHeaders().containsKey("Upgrade")
                || exchange.getRequestHeaders().containsKey("HTTP2-Settings");
        calls.add(new Call(exchange.getRequestMethod(), path + (query == null ? "" : "?" + query),
                String.valueOf(exchange.getRequestHeaders().getFirst("X-Auth-User-Role")), body, upgrade));
        Response response = responses.getOrDefault(exchange.getRequestMethod() + " " + path, new Response(404, ""));
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }
}
