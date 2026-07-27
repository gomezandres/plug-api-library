package com.plug.http.exception;

/** Thrown when a request (including all retries) ultimately fails due to a timeout. */
public class HttpTimeoutException extends PlugHttpException {

    public HttpTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
