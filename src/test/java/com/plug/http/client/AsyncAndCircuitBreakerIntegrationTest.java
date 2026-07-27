package com.plug.http.client;

import com.plug.http.exception.CircuitBreakerOpenException;
import com.plug.http.resilience.CircuitBreakerConfig;
import com.plug.http.testsupport.Order;
import com.plug.http.testsupport.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncAndCircuitBreakerIntegrationTest {

    private TestHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void executeAsyncResolvesWithTypedBody() throws Exception {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 200, "{\"id\":7,\"status\":\"NEW\"}"));
        PlugHttpClient client = PlugHttpClient.builder().baseUri(server.baseUri()).build();

        CompletableFuture<Order> future = client.get("/orders/7").executeAsync(Order.class);

        Order order = future.get();
        assertEquals(7, order.id());
    }

    @Test
    void circuitBreakerOpensAndShortCircuitsSubsequentCalls() {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 500, "{}"));
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .circuitBreaker(CircuitBreakerConfig.builder().failureThreshold(2).openDuration(Duration.ofMinutes(1)).build())
            .build();

        assertThrows(RuntimeException.class, () -> client.get("/boom").executeString());
        assertThrows(RuntimeException.class, () -> client.get("/boom").executeString());

        assertThrows(CircuitBreakerOpenException.class, () -> client.get("/boom").executeString());
    }
}
