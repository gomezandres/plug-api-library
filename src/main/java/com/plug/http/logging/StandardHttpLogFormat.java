package com.plug.http.logging;

import com.plug.http.json.DefaultJsonCodec;
import com.plug.http.json.JsonCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts an {@link HttpLogEvent} into the company's standard structured log shape — an envelope
 * ({@code level}/{@code thread}/{@code logger}) wrapping a {@code message} with
 * {@code type}/{@code id}/{@code address}-or-{@code responseCode}/{@code method}/{@code headers}/
 * {@code payload} — and writes it to {@code System.out} as one JSON line.
 * <p>
 * This is a ready-to-use {@link HttpLogSink} for services that don't have a structured-logging
 * setup of their own yet. Services with a real JSON log encoder (e.g. logstash-logback-encoder)
 * should instead build their own {@code HttpLogSink} that hands {@code message}'s fields to that
 * encoder directly, so the envelope reflects the real logger/thread and the actual call site —
 * confirm the exact wiring with the owner of that logging setup before relying on it in
 * production.
 */
public final class StandardHttpLogFormat {

    private static final Map<Integer, String> REASON_PHRASES = Map.ofEntries(
        Map.entry(200, "OK"), Map.entry(201, "Created"), Map.entry(204, "No Content"),
        Map.entry(400, "Bad Request"), Map.entry(401, "Unauthorized"), Map.entry(403, "Forbidden"), Map.entry(404, "Not Found"),
        Map.entry(500, "Internal Server Error"), Map.entry(502, "Bad Gateway"), Map.entry(503, "Service Unavailable"), Map.entry(504, "Gateway Timeout"));

    private static final JsonCodec JSON = new DefaultJsonCodec();

    private StandardHttpLogFormat() {
    }

    public static void logAsStandardJson(HttpLogEvent event) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", event.phase() == HttpLogPhase.OUTBOUND ? "Outbound Message" : "Inbound Message");
        message.put("id", event.correlationId());
        if (event.phase() == HttpLogPhase.OUTBOUND) {
            message.put("address", event.uri().toString());
            message.put("method", event.method());
        } else {
            message.put("responseCode", event.statusCode() + " " + REASON_PHRASES.getOrDefault(event.statusCode(), ""));
        }
        message.put("headers", formatHeaders(event.headers()));
        message.put("payload", event.body());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("level", event.level());
        envelope.put("thread", Thread.currentThread().getName());
        envelope.put("logger", "a.c.p.util.log.LoggerClientInterceptor");
        envelope.put("message", message);

        System.out.println(new String(JSON.serialize(envelope)));
    }

    private static String formatHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var entry : headers.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(entry.getKey()).append(":\"").append(entry.getValue()).append("\"");
        }
        return sb.append("]").toString();
    }
}
