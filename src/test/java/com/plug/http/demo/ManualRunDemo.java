package com.plug.http.demo;

import com.plug.http.client.PlugHttpClient;
import com.plug.http.logging.StandardHttpLogFormat;

/**
 * Shows a client wired to {@link StandardHttpLogFormat#logAsStandardJson}, the library's
 * ready-to-use adapter from {@code HttpLogEvent} to the company's standard structured log shape.
 */
public class ManualRunDemo {

    public static void main(String[] args) {
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri("https://echo.free.beeceptor.com/")
            .sendCorrelationIdHeader(true)
            .logHeaders(true)
            .logBody(true)
            .logSink(StandardHttpLogFormat::logAsStandardJson)
            .build();

        var response = client.get("/console/prueba-http-client-library")
            .throwOnError(false)
            .executeString();
    }
}
