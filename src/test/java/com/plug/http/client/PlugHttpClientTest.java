package com.plug.http.client;

import com.plug.http.exception.HttpStatusException;
import com.plug.http.testsupport.Order;
import com.plug.http.testsupport.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlugHttpClientTest {

    private TestHttpServer server;
    private PlugHttpClient client;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void getReturnsTypedJsonBody() {
        server = TestHttpServer.start((exchange) ->
            TestHttpServer.respond(exchange, 200, "{\"id\":123,\"status\":\"NEW\"}"));
        client = PlugHttpClient.builder().baseUri(server.baseUri()).build();

        Order order = client.get("/orders/{id}").pathParam("id", 123).execute(Order.class);

        assertEquals(123, order.id());
        assertEquals("NEW", order.status());
    }

    @Test
    void postSerializesBodyAsJsonAndReturnsTypedResponse() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        server = TestHttpServer.start((exchange) -> {
            capturedBody.set(TestHttpServer.readBody(exchange));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            TestHttpServer.respond(exchange, 201, "{\"id\":1,\"status\":\"CREATED\"}");
        });
        client = PlugHttpClient.builder().baseUri(server.baseUri()).build();

        Order created = client.post("/orders").body(new Order(0, "NEW")).execute(Order.class);

        assertEquals("CREATED", created.status());
        assertTrue(capturedBody.get().contains("\"status\":\"NEW\""));
        assertTrue(capturedContentType.get().startsWith("application/json"));
    }

    @Test
    void queryAndPathParamsAreEncoded() {
        AtomicReference<String> capturedPathAndQuery = new AtomicReference<>();
        server = TestHttpServer.start((exchange) -> {
            capturedPathAndQuery.set(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
            TestHttpServer.respond(exchange, 200, "{}");
        });
        client = PlugHttpClient.builder().baseUri(server.baseUri()).build();

        client.get("/search/{term}")
            .pathParam("term", "a/b c")
            .queryParam("q", "hello world")
            .executeString();

        assertEquals("/search/a%2Fb%20c?q=hello%20world", capturedPathAndQuery.get());
    }

    @Test
    void nonSuccessStatusThrowsHttpStatusException() {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 404, "{\"error\":\"not found\"}"));
        client = PlugHttpClient.builder().baseUri(server.baseUri()).build();

        HttpStatusException exception = assertThrows(HttpStatusException.class,
            () -> client.get("/orders/999").execute(Order.class));

        assertEquals(404, exception.statusCode());
        assertTrue(exception.rawBody().contains("not found"));
    }

    @Test
    void throwOnErrorFalseReturnsRawResponse() {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 500, "{}"));
        client = PlugHttpClient.builder().baseUri(server.baseUri()).build();

        var response = client.get("/boom").throwOnError(false).executeString();

        assertEquals(500, response.statusCode());
    }
}
