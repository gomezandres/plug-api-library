package com.plug.http.request;

import java.nio.charset.StandardCharsets;

/** RFC 3986 percent-encoding helpers for path segments and query components. */
final class UriEncoding {

    private static final String EXTRA_UNRESERVED = "-._~";

    private UriEncoding() {
    }

    static String encodePathSegment(String value) {
        return encode(value);
    }

    static String encodeQueryComponent(String value) {
        return encode(value);
    }

    private static String encode(String value) {
        StringBuilder result = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if (isUnreserved(c)) {
                result.append(c);
            } else {
                result.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return result.toString();
    }

    private static boolean isUnreserved(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
            || EXTRA_UNRESERVED.indexOf(c) >= 0;
    }
}
