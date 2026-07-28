package com.plug.http.demo;

import com.plug.http.client.PlugHttpClient;
import com.plug.http.json.DefaultJsonCodec;
import com.plug.http.json.JsonCodec;
import com.plug.http.logging.HttpLogEvent;
import com.plug.http.logging.HttpLogPhase;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows how a service can adapt {@link HttpLogEvent} into the QA-style structured log shape
 * (type/id/address-or-responseCode/method/headers/payload nested under a "message" field).
 * The {@code @timestamp}/{@code level}/{@code thread}/{@code logger} envelope fields are normally
 * added automatically by the host's own JSON log encoder (e.g. logstash-logback-encoder) once
 * this map is passed as the log statement's argument — they are stamped in here only so this
 * demo's stdout output is self-contained.
 */
public class ManualRunDemo {

    private static final Map<Integer, String> REASON_PHRASES = Map.ofEntries(
        Map.entry(200, "OK"), Map.entry(201, "Created"), Map.entry(204, "No Content"),
        Map.entry(400, "Bad Request"), Map.entry(401, "Unauthorized"), Map.entry(403, "Forbidden"), Map.entry(404, "Not Found"),
        Map.entry(500, "Internal Server Error"), Map.entry(502, "Bad Gateway"), Map.entry(503, "Service Unavailable"), Map.entry(504, "Gateway Timeout"));

    private static final JsonCodec JSON = new DefaultJsonCodec();

    public static void main(String[] args) {
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri("https://echo.free.beeceptor.com/")
            .logHeaders(true)
            .logBody(true)
            .logSink(ManualRunDemo::logAsQaJson)
            .build();

        var response = client.get("/console/prueba-http-client-library")
            .throwOnError(false)
            .executeString();

        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
    }

    private static void logAsQaJson(HttpLogEvent event) {
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
