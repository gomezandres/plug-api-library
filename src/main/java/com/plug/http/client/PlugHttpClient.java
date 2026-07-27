package com.plug.http.client;

import com.plug.http.exception.CircuitBreakerOpenException;
import com.plug.http.exception.HttpStatusException;
import com.plug.http.exception.HttpTimeoutException;
import com.plug.http.exception.PlugHttpException;
import com.plug.http.json.DefaultJsonCodec;
import com.plug.http.json.JsonCodec;
import com.plug.http.logging.HttpLogEvent;
import com.plug.http.logging.HttpLogLevel;
import com.plug.http.logging.HttpLogLevelPolicy;
import com.plug.http.logging.HttpLogSink;
import com.plug.http.request.HttpRequestSpec;
import com.plug.http.resilience.CircuitBreaker;
import com.plug.http.resilience.CircuitBreakerConfig;
import com.plug.http.resilience.ExponentialBackoffRetryPolicy;
import com.plug.http.resilience.RetryPolicy;
import com.plug.http.response.PlugHttpResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Company-standard native Java HTTP client. Framework-agnostic — built only on
 * {@code java.net.http.HttpClient}. Thread-safe and cheap to share as a singleton once built,
 * exactly like {@code java.net.http.HttpClient} itself.
 *
 * <pre>{@code
 * PlugHttpClient client = PlugHttpClient.builder()
 *     .baseUri("https://api.example.com")
 *     .logSink(event -> companyLogger.log(event))
 *     .build();
 *
 * Order order = client.get("/orders/{id}").pathParam("id", 123).execute(Order.class);
 * }</pre>
 */
public final class PlugHttpClient {

    private final HttpClient jdkClient;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final Map<String, String> defaultHeaders;
    private final JsonCodec jsonCodec;
    private final RetryPolicy retryPolicy;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final HttpLogSink logSink;
    private final HttpLogLevelPolicy logLevelPolicy;
    private final String correlationIdHeader;
    private final Supplier<String> correlationIdSupplier;
    private final Executor callbackExecutor;

    private PlugHttpClient(Builder builder) {
        this.baseUri = builder.baseUri;
        this.requestTimeout = builder.requestTimeout;
        this.defaultHeaders = Map.copyOf(builder.defaultHeaders);
        this.jsonCodec = builder.jsonCodec;
        this.retryPolicy = builder.retryPolicy;
        this.circuitBreakerConfig = builder.circuitBreakerConfig;
        this.logSink = builder.logSink;
        this.logLevelPolicy = builder.logLevelPolicy;
        this.correlationIdHeader = builder.correlationIdHeader;
        this.correlationIdSupplier = builder.correlationIdSupplier;
        this.callbackExecutor = builder.executor;
        this.jdkClient = HttpClient.newBuilder()
            .connectTimeout(builder.connectTimeout)
            .executor(builder.executor)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public HttpRequestSpec get(String path) {
        return new HttpRequestSpec(this, "GET", path);
    }

    public HttpRequestSpec post(String path) {
        return new HttpRequestSpec(this, "POST", path);
    }

    public HttpRequestSpec put(String path) {
        return new HttpRequestSpec(this, "PUT", path);
    }

    public HttpRequestSpec delete(String path) {
        return new HttpRequestSpec(this, "DELETE", path);
    }

    public HttpRequestSpec patch(String path) {
        return new HttpRequestSpec(this, "PATCH", path);
    }

    /**
     * Runs the full request pipeline (correlation id, circuit breaker, retries, interceptors,
     * JSON decoding). Called by {@link HttpRequestSpec} — not intended to be called directly
     * by library consumers.
     */
    public <T> CompletableFuture<PlugHttpResponse<T>> executeInternal(HttpRequestSpec spec, Class<T> responseType) {
        URI uri = spec.resolvedUri(baseUri);
        String correlationId = correlationIdSupplier.get();
        String authority = uri.getAuthority();
        CircuitBreaker breaker = circuitBreakerConfig == null
            ? null
            : circuitBreakers.computeIfAbsent(authority, key -> new CircuitBreaker(circuitBreakerConfig));

        if (breaker != null && !breaker.tryAcquire()) {
            return CompletableFuture.failedFuture(new CircuitBreakerOpenException(authority));
        }

        return attempt(spec, uri, correlationId, breaker, 1)
            .thenApply(response -> buildResult(spec, responseType, response));
    }

    private CompletableFuture<HttpResponse<byte[]>> attempt(
            HttpRequestSpec spec, URI uri, String correlationId, CircuitBreaker breaker, int attemptNumber) {

        HttpRequest request = buildRequest(spec, uri, correlationId);
        long start = System.nanoTime();

        return jdkClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .handle((response, error) -> {
                long durationMillis = (System.nanoTime() - start) / 1_000_000;
                int statusCode = response != null ? response.statusCode() : -1;
                recordOutcome(breaker, statusCode, error);

                boolean willRetry = retryPolicy.shouldRetry(spec.method(), attemptNumber, statusCode, error);
                emitLog(spec, uri, correlationId, attemptNumber, willRetry, statusCode, durationMillis, error);

                if (willRetry) {
                    Duration delay = retryPolicy.backoff(attemptNumber);
                    return CompletableFuture
                        .supplyAsync(() -> (HttpResponse<byte[]>) null,
                            CompletableFuture.delayedExecutor(Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS, callbackExecutor))
                        .thenCompose(ignored -> attempt(spec, uri, correlationId, breaker, attemptNumber + 1));
                }
                if (error != null) {
                    CompletableFuture<HttpResponse<byte[]>> failed = new CompletableFuture<>();
                    failed.completeExceptionally(mapException(spec.method(), uri, error));
                    return failed;
                }
                return CompletableFuture.completedFuture(response);
            })
            .thenCompose(stage -> stage);
    }

    private void emitLog(HttpRequestSpec spec, URI uri, String correlationId, int attempt, boolean willRetry,
                          int statusCode, long durationMillis, Throwable error) {
        HttpLogLevel level = logLevelPolicy.level(spec.method(), attempt, willRetry, statusCode, error);
        HttpLogEvent event = new HttpLogEvent(
            level, "http.client.request", correlationId, spec.method(), uri, attempt, willRetry, statusCode, durationMillis, error);
        try {
            logSink.log(event);
        } catch (RuntimeException ignored) {
            // a broken log sink must never break the request pipeline
        }
    }

    private void recordOutcome(CircuitBreaker breaker, int statusCode, Throwable error) {
        if (breaker == null) {
            return;
        }
        if (error == null && statusCode < 500) {
            breaker.recordSuccess();
        } else {
            breaker.recordFailure();
        }
    }

    private PlugHttpException mapException(String method, URI uri, Throwable cause) {
        if (cause instanceof PlugHttpException plugHttpException) {
            return plugHttpException;
        }
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return new HttpTimeoutException("Timed out calling " + method + " " + uri, cause);
        }
        return new PlugHttpException("HTTP request failed calling " + method + " " + uri, cause);
    }

    private HttpRequest buildRequest(HttpRequestSpec spec, URI uri, String correlationId) {
        byte[] requestBody = resolveRequestBody(spec);
        HttpRequest.BodyPublisher publisher = requestBody == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(requestBody);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .method(spec.method(), publisher);

        defaultHeaders.forEach(requestBuilder::header);
        spec.headers().forEach(requestBuilder::header);

        if (!hasHeaderIgnoreCase(spec.headers(), correlationIdHeader) && !hasHeaderIgnoreCase(defaultHeaders, correlationIdHeader)) {
            requestBuilder.header(correlationIdHeader, correlationId);
        }

        if (requestBody != null && !(spec.rawBody() instanceof byte[])
            && !hasHeaderIgnoreCase(spec.headers(), "Content-Type")
            && !hasHeaderIgnoreCase(defaultHeaders, "Content-Type")) {
            requestBuilder.header("Content-Type", "application/json; charset=UTF-8");
        }

        return requestBuilder.build();
    }

    private static boolean hasHeaderIgnoreCase(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    private byte[] resolveRequestBody(HttpRequestSpec spec) {
        Object requestBody = spec.rawBody();
        if (requestBody == null) {
            return null;
        }
        if (requestBody instanceof byte[] bytes) {
            return bytes;
        }
        if (requestBody instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        return jsonCodec.serialize(requestBody);
    }

    private <T> PlugHttpResponse<T> buildResult(HttpRequestSpec spec, Class<T> responseType, HttpResponse<byte[]> response) {
        int statusCode = response.statusCode();
        byte[] rawBody = response.body();
        if (spec.throwOnError() && (statusCode < 200 || statusCode >= 300)) {
            throw new HttpStatusException(statusCode,
                rawBody != null ? new String(rawBody, StandardCharsets.UTF_8) : "",
                response.headers());
        }
        return new PlugHttpResponse<>(statusCode, response.headers(), decodeBody(responseType, rawBody));
    }

    @SuppressWarnings("unchecked")
    private <T> T decodeBody(Class<T> responseType, byte[] rawBody) {
        if (responseType == Void.class) {
            return null;
        }
        if (responseType == byte[].class) {
            return (T) rawBody;
        }
        if (responseType == String.class) {
            return (T) (rawBody != null ? new String(rawBody, StandardCharsets.UTF_8) : "");
        }
        if (rawBody == null || rawBody.length == 0) {
            return null;
        }
        return jsonCodec.deserialize(rawBody, responseType);
    }

    public static final class Builder {
        private URI baseUri;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private JsonCodec jsonCodec = new DefaultJsonCodec();
        private RetryPolicy retryPolicy = ExponentialBackoffRetryPolicy.builder().maxAttempts(1).build();
        private CircuitBreakerConfig circuitBreakerConfig;
        private HttpLogSink logSink = HttpLogSink.noOp();
        private HttpLogLevelPolicy logLevelPolicy = HttpLogLevelPolicy.defaultPolicy();
        private String correlationIdHeader = "X-Correlation-Id";
        private Supplier<String> correlationIdSupplier = () -> UUID.randomUUID().toString();
        private Executor executor = Executors.newVirtualThreadPerTaskExecutor();

        private Builder() {
        }

        public Builder baseUri(String baseUri) {
            URI uri = URI.create(baseUri);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException(
                    "baseUri must be an absolute URI, e.g. https://api.example.com — got: " + baseUri);
            }
            this.baseUri = uri;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder defaultHeader(String name, String value) {
            defaultHeaders.put(name, value);
            return this;
        }

        public Builder jsonCodec(JsonCodec jsonCodec) {
            this.jsonCodec = jsonCodec;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder circuitBreaker(CircuitBreakerConfig circuitBreakerConfig) {
            this.circuitBreakerConfig = circuitBreakerConfig;
            return this;
        }

        public Builder logSink(HttpLogSink logSink) {
            this.logSink = logSink;
            return this;
        }

        public Builder logLevelPolicy(HttpLogLevelPolicy logLevelPolicy) {
            this.logLevelPolicy = logLevelPolicy;
            return this;
        }

        public Builder correlationIdHeader(String correlationIdHeader) {
            this.correlationIdHeader = correlationIdHeader;
            return this;
        }

        public Builder correlationIdSupplier(Supplier<String> correlationIdSupplier) {
            this.correlationIdSupplier = correlationIdSupplier;
            return this;
        }

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public PlugHttpClient build() {
            if (baseUri == null) {
                throw new IllegalStateException("baseUri is required — call builder.baseUri(\"https://...\") before build()");
            }
            return new PlugHttpClient(this);
        }
    }
}
