package com.plug.http.logging;

/**
 * Decides the {@link HttpLogLevel} for one attempt. Configurable per client — supply your own
 * via {@code PlugHttpClient.builder().logLevelPolicy(...)}, or use {@link #defaultPolicy()}.
 */
@FunctionalInterface
public interface HttpLogLevelPolicy {

    HttpLogLevel level(String method, int attempt, boolean willRetry, int statusCode, Throwable error);

    /**
     * Success (2xx) → INFO. Client error (4xx) → WARN. Server error, timeout, no response, or
     * network failure → WARN if it will still be retried, ERROR once it's the final attempt.
     */
    static HttpLogLevelPolicy defaultPolicy() {
        return (method, attempt, willRetry, statusCode, error) -> {
            if (error != null || statusCode < 0 || statusCode >= 500) {
                return willRetry ? HttpLogLevel.WARN : HttpLogLevel.ERROR;
            }
            if (statusCode >= 400) {
                return HttpLogLevel.WARN;
            }
            return HttpLogLevel.INFO;
        };
    }
}
