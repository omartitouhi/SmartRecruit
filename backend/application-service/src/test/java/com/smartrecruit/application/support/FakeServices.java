package com.smartrecruit.application.support;

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
 * One JDK HTTP server playing job-service, user-service, ai-service and dashboard-service: the four
 * {@code services.*-uri} properties point to it and routes are told apart by path.
 */
public final class FakeServices {

    public record Call(String method, String path, String query, String body, String role, boolean h2cUpgrade) {
    }

    private record Response(int status, String body) {
    }

    private static final HttpServer SERVER;
    private static final Map<String, Response> ROUTES = new ConcurrentHashMap<>();
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVER.createContext("/", FakeServices::handle);
        SERVER.start();
    }

    private FakeServices() {
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean upgrade = exchange.getRequestHeaders().containsKey("Upgrade")
                || exchange.getRequestHeaders().containsKey("HTTP2-Settings");
        CALLS.add(new Call(method, path, exchange.getRequestURI().getQuery(), body,
                exchange.getRequestHeaders().getFirst("X-Auth-User-Role"), upgrade));
        Response response = ROUTES.getOrDefault(method + " " + path, new Response(404, ""));
        byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        }
        exchange.close();
    }

    public static String uri() {
        return "http://localhost:" + SERVER.getAddress().getPort();
    }

    public static void reset() {
        ROUTES.clear();
        CALLS.clear();
    }

    /** @param route {@code "GET /internal/v1/jobs/<id>"}: method and path, without query */
    public static void on(String route, int status, String body) {
        ROUTES.put(route, new Response(status, body));
    }

    public static List<Call> calls(String route) {
        return CALLS.stream().filter(call -> (call.method() + " " + call.path()).equals(route)).toList();
    }
}
