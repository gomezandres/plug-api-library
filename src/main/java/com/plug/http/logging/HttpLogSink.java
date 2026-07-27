package com.plug.http.logging;

/**
 * Bridge between this library's standardized {@link HttpLogEvent} and whatever logging
 * backend the host application actually uses. This library has zero compile-time dependency
 * on SLF4J, Log4j2, or any corporate logging library — implement this with a one-line
 * adapter that hands the event to that backend.
 *
 * <p>A sink that throws is caught and ignored by the client: a broken sink must never break
 * an actual HTTP call.
 */
@FunctionalInterface
public interface HttpLogSink {

    void log(HttpLogEvent event);

    static HttpLogSink noOp() {
        return event -> {
        };
    }
}
