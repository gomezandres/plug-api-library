package com.plug.http.exception;

/** Base unchecked exception for every failure raised by this library. */
public class PlugHttpException extends RuntimeException {

    public PlugHttpException(String message) {
        super(message);
    }

    public PlugHttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
