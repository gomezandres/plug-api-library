package com.plug.http.logging;

/**
 * Severity of an {@link HttpLogEvent}. Deliberately not SLF4J's own level type — this library
 * has zero dependency on any logging framework. An {@link HttpLogSink} implementation maps
 * this to whatever the host application's logging backend uses (SLF4J, Log4j2, a corporate
 * logging library, etc.).
 */
public enum HttpLogLevel {
    DEBUG, INFO, WARN, ERROR
}
