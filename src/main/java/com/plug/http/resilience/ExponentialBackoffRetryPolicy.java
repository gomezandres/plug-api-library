package com.plug.http.resilience;

import java.time.Duration;
import java.util.Set;

/**
 * Default {@link RetryPolicy}: retries only idempotent HTTP methods (GET, HEAD, PUT, DELETE,
 * OPTIONS) on network/timeout errors or a configurable set of retryable status codes, with
 * exponential backoff. POST and PATCH are never retried by this policy — supply a custom
 * {@link RetryPolicy} if a specific call needs different semantics.
 */
public final class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private static final Set<String> IDEMPOTENT_METHODS = Set.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS");

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Set<Integer> retryableStatusCodes;

    private ExponentialBackoffRetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.baseDelay = builder.baseDelay;
        this.retryableStatusCodes = Set.copyOf(builder.retryableStatusCodes);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean shouldRetry(String method, int attempt, int statusCode, Throwable error) {
        if (attempt >= maxAttempts) {
            return false;
        }
        if (!IDEMPOTENT_METHODS.contains(method)) {
            return false;
        }
        if (error != null) {
            return true;
        }
        return retryableStatusCodes.contains(statusCode);
    }

    @Override
    public Duration backoff(int attempt) {
        long millis = baseDelay.toMillis() * (1L << Math.max(0, attempt - 1));
        return Duration.ofMillis(millis);
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration baseDelay = Duration.ofMillis(200);
        private Set<Integer> retryableStatusCodes = Set.of(502, 503, 504);

        private Builder() {
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder baseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
            return this;
        }

        public Builder retryableStatusCodes(Set<Integer> retryableStatusCodes) {
            this.retryableStatusCodes = retryableStatusCodes;
            return this;
        }

        public ExponentialBackoffRetryPolicy build() {
            return new ExponentialBackoffRetryPolicy(this);
        }
    }
}
