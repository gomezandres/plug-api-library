package com.plug.http.logging;

import java.net.URI;
import java.util.Map;

/**
 * The standardized shape of one side of an HTTP call attempt, as decided by this library —
 * every microservice that uses {@code PlugHttpClient} produces the exact same fields for the
 * exact same kind of event. Two events are emitted per attempt (including retries): one
 * {@link HttpLogPhase#OUTBOUND} right before the request is sent, and one
 * {@link HttpLogPhase#INBOUND} once the response (or failure) for that attempt is known.
 *
 * @param level         severity; always {@link HttpLogLevel#INFO} for {@code OUTBOUND} events,
 *                      decided by the configured {@link HttpLogLevelPolicy} for {@code INBOUND}
 *                      events
 * @param phase         which side of the attempt this event describes
 * @param eventName     always {@code "http.client.request"} — a stable name to filter/query on
 * @param correlationId stable across both phases and every retry attempt of one logical call
 * @param method        HTTP method
 * @param uri           the fully resolved request URI
 * @param attempt       1-based attempt number
 * @param willRetry     true if this attempt failed and will be retried; always {@code false} on
 *                      {@code OUTBOUND} events, since the outcome isn't known yet
 * @param statusCode    response status code; {@code -1} on {@code OUTBOUND} events or if no
 *                      response was received
 * @param durationMillis wall-clock duration of this attempt so far; {@code 0} on {@code OUTBOUND}
 *                       events
 * @param error         failure cause; always {@code null} on {@code OUTBOUND} events
 * @param headers       request headers on {@code OUTBOUND}, response headers on {@code INBOUND},
 *                      redacted by the client's {@link SensitiveDataMasker}; null unless
 *                      {@code PlugHttpClient.builder().logHeaders(true)}, or if {@code INBOUND}
 *                      and no response was received
 * @param body          request body on {@code OUTBOUND}, response body on {@code INBOUND},
 *                      redacted the same way; null under the same conditions as {@code headers}
 */
public record HttpLogEvent(
    HttpLogLevel level,
    HttpLogPhase phase,
    String eventName,
    String correlationId,
    String method,
    URI uri,
    int attempt,
    boolean willRetry,
    int statusCode,
    long durationMillis,
    Throwable error,
    Map<String, String> headers,
    String body
) {
}
