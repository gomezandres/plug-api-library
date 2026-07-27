package com.plug.http.exception;

/** Thrown when a {@link com.plug.http.json.JsonCodec} fails to serialize or deserialize a body. */
public class JsonCodecException extends PlugHttpException {

    public JsonCodecException(String message) {
        super(message);
    }

    public JsonCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
