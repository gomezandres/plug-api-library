package com.plug.http.json;

/**
 * Pluggable JSON (de)serialization contract. Implement this to swap in Gson, a Spring
 * {@code ObjectMapper}, or anything else instead of the bundled Jackson-based default.
 */
public interface JsonCodec {

    byte[] serialize(Object value);

    <T> T deserialize(byte[] bytes, Class<T> type);
}
