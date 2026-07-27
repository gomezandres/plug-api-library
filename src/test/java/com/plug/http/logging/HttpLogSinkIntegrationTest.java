package com.plug.http.logging;

import com.plug.http.client.PlugHttpClient;
import com.plug.http.resilience.ExponentialBackoffRetryPolicy;
import com.plug.http.testsupport.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpLogSinkIntegrationTest {

    private TestHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void oneEventIsEmittedPerAttemptWithStableCorrelationIdAndDefaultLevels() {
        AtomicInteger requestCount = new AtomicInteger();
        server = TestHttpServer.start((exchange) -> {
            int attempt = requestCount.incrementAndGet();
            TestHttpServer.respond(exchange, attempt < 2 ? 503 : 200, "{}");
        });

        List<HttpLogEvent> events = new CopyOnWriteArrayList<>();
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .retryPolicy(ExponentialBackoffRetryPolicy.builder().maxAttempts(2).baseDelay(Duration.ofMillis(1)).build())
            .logSink(events::add)
            .build();

        client.get("/flaky").executeString();

        assertEquals(2, events.size());
        assertEquals("http.client.request", events.get(0).eventName());
        assertEquals(1, events.get(0).attempt());
        assertEquals(2, events.get(1).attempt());
        assertEquals(events.get(0).correlationId(), events.get(1).correlationId());
        assertEquals(503, events.get(0).statusCode());
        assertTrue(events.get(0).willRetry());
        assertEquals(HttpLogLevel.WARN, events.get(0).level());
        assertEquals(200, events.get(1).statusCode());
        assertEquals(HttpLogLevel.INFO, events.get(1).level());
    }

    @Test
    void throwingLogSinkDoesNotBreakTheCall() {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 200, "{}"));
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .logSink(event -> {
                throw new IllegalStateException("boom");
            })
            .build();

        var response = client.get("/health").executeString();

        assertEquals(200, response.statusCode());
    }

    @Test
    void customLogLevelPolicyIsHonored() {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 200, "{}"));
        List<HttpLogEvent> events = new CopyOnWriteArrayList<>();
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .logSink(events::add)
            .logLevelPolicy((method, attempt, willRetry, statusCode, error) -> HttpLogLevel.DEBUG)
            .build();

        client.get("/health").executeString();

        assertEquals(HttpLogLevel.DEBUG, events.get(0).level());
    }

    @Test
    void headersAndBodyAreOmittedByDefault() {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 200, "{}"));
        List<HttpLogEvent> events = new CopyOnWriteArrayList<>();
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .logSink(events::add)
            .build();

        client.post("/login").body("{\"password\":\"hunter2\"}").executeString();

        assertEquals(null, events.get(0).requestHeaders());
        assertEquals(null, events.get(0).requestBody());
        assertEquals(null, events.get(0).responseHeaders());
        assertEquals(null, events.get(0).responseBody());
    }

    @Test
    void headersAndBodyAreRedactedWhenEnabled() {
        server = TestHttpServer.start((exchange) -> TestHttpServer.respond(exchange, 200, "{\"token\":\"abc\"}"));
        List<HttpLogEvent> events = new CopyOnWriteArrayList<>();
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri(server.baseUri())
            .logSink(events::add)
            .logHeaders(true)
            .logBody(true)
            .build();

        client.post("/login")
            .header("Authorization", "Bearer secret")
            .body("{\"password\":\"hunter2\",\"name\":\"Andres\"}")
            .executeString();

        HttpLogEvent event = events.get(0);
        assertEquals("***", event.requestHeaders().get("Authorization"));
        assertEquals("{\"password\":\"***\",\"name\":\"Andres\"}", event.requestBody());
        assertEquals("{\"token\":\"***\"}", event.responseBody());
    }
}
