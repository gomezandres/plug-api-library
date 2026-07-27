package com.plug.http.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.plug.http.exception.JsonCodecException;

/**
 * Default {@link JsonCodec} backed by Jackson. Jackson is declared as an optional Maven
 * dependency on this library, so this class is only touched when a JSON convenience method
 * is actually invoked (see {@link DefaultJsonCodec}) — it is never required just to send raw
 * or plain-text requests.
 */
public class JacksonJsonCodec implements JsonCodec {

    private final ObjectMapper objectMapper;

    public JacksonJsonCodec() {
        this(new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    public JacksonJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to serialize request body to JSON", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        try {
            return objectMapper.readValue(bytes, type);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to deserialize JSON response body into " + type.getName(), e);
        }
    }
}
