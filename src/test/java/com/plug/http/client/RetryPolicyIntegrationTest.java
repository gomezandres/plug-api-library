package com.plug.http.client;

import com.plug.http.resilience.ExponentialBackoffRetryPolicy;
import com.plug.http.testsupport.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPolicyIntegrationTest {

    private TestHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void getIsRetriedUntilSuccess() {
        AtomicInteger requestCount = new AtomicInteger();
        server = TestHttpServer.start((exchange) -> {
            int attempt = requestCount.incrementAndGet();
            if (attempt < 3) {
                TestHttpServer.respond(exchange, 503, "{}");
            } else {
                TestHttpServer.respond(exchange, 200, "{}");
            }
        });
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .retryPolicy(ExponentialBackoffRetryPolicy.builder().maxAttempts(3).baseDelay(Duration.ofMillis(1)).build())
            .build();

        var response = client.get("/flaky").executeString();

        assertEquals(200, response.statusCode());
        assertEquals(3, requestCount.get());
    }

    @Test
    void postIsNotRetriedByDefaultPolicy() {
        AtomicInteger requestCount = new AtomicInteger();
        server = TestHttpServer.start((exchange) -> {
            requestCount.incrementAndGet();
            TestHttpServer.respond(exchange, 503, "{}");
        });
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .retryPolicy(ExponentialBackoffRetryPolicy.builder().maxAttempts(3).baseDelay(Duration.ofMillis(1)).build())
            .build();

        assertThrows(RuntimeException.class, () -> client.post("/flaky").executeString());
        assertEquals(1, requestCount.get());
    }
}
