package com.plug.http.exception;

import java.net.http.HttpHeaders;

/** Thrown when a response has a non-2xx status code and the call has not opted out via {@code throwOnError(false)}. */
public class HttpStatusException extends PlugHttpException {

    private final int statusCode;
    private final String rawBody;
    private final HttpHeaders headers;

    public HttpStatusException(int statusCode, String rawBody, HttpHeaders headers) {
        super("HTTP request failed with status " + statusCode);
        this.statusCode = statusCode;
        this.rawBody = rawBody;
        this.headers = headers;
    }

    public int statusCode() {
        return statusCode;
    }

    public String rawBody() {
        return rawBody;
    }

    public HttpHeaders headers() {
        return headers;
    }
}
