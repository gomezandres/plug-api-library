package com.plug.http.logging;

import java.net.URI;
import java.util.Map;

/**
 * The standardized shape of one HTTP call attempt, as decided by this library — every
 * microservice that uses {@code PlugHttpClient} produces the exact same fields for the exact
 * same kind of event. One event is emitted per attempt (including retries), after it
 * completes.
 *
 * @param level         severity, decided by the configured {@link HttpLogLevelPolicy}
 * @param eventName     always {@code "http.client.request"} — a stable name to filter/query on
 * @param correlationId stable across every retry attempt of one logical call
 * @param method        HTTP method
 * @param uri           the fully resolved request URI
 * @param attempt       1-based attempt number
 * @param willRetry     true if this failed attempt will be retried
 * @param statusCode    response status code, or -1 if no response was received
 * @param durationMillis wall-clock duration of this attempt
 * @param error         failure cause, or null if a response was received
 * @param requestHeaders  request headers, redacted by the client's {@link SensitiveDataMasker};
 *                        null unless {@code PlugHttpClient.builder().logHeaders(true)}
 * @param requestBody     request body, redacted by the client's {@link SensitiveDataMasker};
 *                        null unless {@code PlugHttpClient.builder().logBody(true)}
 * @param responseHeaders response headers, redacted the same way; null under the same conditions
 *                        as {@code requestHeaders}, or if no response was received
 * @param responseBody    response body, redacted the same way; null under the same conditions
 *                        as {@code requestBody}, or if no response was received
 */
public record HttpLogEvent(
    HttpLogLevel level,
    String eventName,
    String correlationId,
    String method,
    URI uri,
    int attempt,
    boolean willRetry,
    int statusCode,
    long durationMillis,
    Throwable error,
    Map<String, String> requestHeaders,
    String requestBody,
    Map<String, String> responseHeaders,
    String responseBody
) {
}
