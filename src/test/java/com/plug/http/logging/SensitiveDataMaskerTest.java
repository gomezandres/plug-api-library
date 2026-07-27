package com.plug.http.logging;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = SensitiveDataMasker.defaultMasker();

    @Test
    void masksKnownSensitiveHeadersCaseAndSeparatorInsensitively() {
        HttpHeaders headers = HttpHeaders.of(
            Map.of(
                "Authorization", List.of("Bearer secret-token"),
                "api-key", List.of("abc123"),
                "X-Correlation-Id", List.of("keep-me")),
            (name, value) -> true);

        Map<String, String> masked = masker.maskHeaders(headers);

        assertEquals("***", masked.get("Authorization"));
        assertEquals("***", masked.get("api-key"));
        assertEquals("keep-me", masked.get("X-Correlation-Id"));
    }

    @Test
    void masksSensitiveJsonBodyFieldsButKeepsOtherFields() {
        String body = "{\"password\":\"hunter2\",\"dni\":30123456,\"name\":\"Andres\"}";

        String masked = masker.maskBody(body);

        assertEquals("{\"password\":\"***\",\"dni\":\"***\",\"name\":\"Andres\"}", masked);
    }

    @Test
    void additionalFieldNamesExtendDefaults() {
        SensitiveDataMasker extended = masker.withAdditionalFieldNames("internalId");
        String body = "{\"internalId\":\"42\",\"name\":\"Andres\"}";

        assertEquals("{\"internalId\":\"***\",\"name\":\"Andres\"}", extended.maskBody(body));
        assertEquals("{\"internalId\":\"42\",\"name\":\"Andres\"}", masker.maskBody(body));
    }

    @Test
    void nullOrEmptyBodyIsReturnedAsIs() {
        assertEquals(null, masker.maskBody(null));
        assertEquals("", masker.maskBody(""));
    }
}
