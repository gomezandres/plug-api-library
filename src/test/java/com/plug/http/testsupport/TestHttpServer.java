package com.plug.http.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Tiny JDK-only HTTP server fixture for integration tests — no WireMock/Mockito needed. */
public final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    private TestHttpServer(HttpServer server) {
        this.server = server;
    }

    public static TestHttpServer start(HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", handler);
            server.start();
            return new TestHttpServer(server);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public static void respond(HttpExchange exchange, int statusCode, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readBody(HttpExchange exchange) {
        try (var in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
