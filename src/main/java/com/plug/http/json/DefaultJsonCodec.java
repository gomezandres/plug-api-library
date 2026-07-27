package com.plug.http.json;

import com.plug.http.exception.JsonCodecException;

/**
 * The {@link JsonCodec} used when the client is built without an explicit one configured.
 * It lazily resolves to {@link JacksonJsonCodec} on first actual use, so a service that
 * never calls a typed JSON method never needs Jackson on its runtime classpath. If Jackson
 * is genuinely missing when a JSON method IS called, it fails with a clear, actionable
 * message instead of a bare {@code NoClassDefFoundError}.
 */
public final class DefaultJsonCodec implements JsonCodec {

    private static final String NOT_AVAILABLE_MESSAGE =
        "No JsonCodec configured and Jackson is not present on the classpath. "
            + "Add 'com.fasterxml.jackson.core:jackson-databind' as a dependency, "
            + "or call PlugHttpClient.builder().jsonCodec(yourCodec) with your own implementation.";

    private volatile JsonCodec delegate;
    private volatile boolean unavailable;

    @Override
    public byte[] serialize(Object value) {
        return resolve().serialize(value);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        return resolve().deserialize(bytes, type);
    }

    private JsonCodec resolve() {
        if (unavailable) {
            throw new JsonCodecException(NOT_AVAILABLE_MESSAGE);
        }
        JsonCodec current = delegate;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (delegate == null) {
                try {
                    delegate = new JacksonJsonCodec();
                } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
                    unavailable = true;
                    throw new JsonCodecException(NOT_AVAILABLE_MESSAGE, e);
                }
            }
            return delegate;
        }
    }
}
