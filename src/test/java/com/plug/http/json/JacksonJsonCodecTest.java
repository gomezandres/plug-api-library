package com.plug.http.json;

import com.plug.http.testsupport.Order;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonJsonCodecTest {

    private final JsonCodec codec = new JacksonJsonCodec();

    @Test
    void roundTripsAPlainObject() {
        byte[] bytes = codec.serialize(new Order(42, "SHIPPED"));
        Order decoded = codec.deserialize(bytes, Order.class);

        assertEquals(42, decoded.id());
        assertEquals("SHIPPED", decoded.status());
    }

    @Test
    void producesReadableJson() {
        byte[] bytes = codec.serialize(new Order(1, "NEW"));
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertEquals(true, json.contains("\"status\":\"NEW\""));
    }
}
