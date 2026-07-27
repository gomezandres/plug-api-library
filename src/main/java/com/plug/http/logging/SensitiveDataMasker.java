package com.plug.http.logging;

import java.net.http.HttpHeaders;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Redacts sensitive header and body field values before they reach an {@link HttpLogSink} — a
 * broken or overly verbose sink must never leak a credential. Body redaction is a best-effort,
 * regex match on {@code "fieldName": value} pairs rather than a real JSON parse, since Jackson
 * is optional in this library and body masking must work without it.
 *
 * <p>Ships with a default field name list covering common credential/token headers and
 * Argentine personal-data fields (DNI, CUIL, CBU, card numbers). Extend it with
 * {@link #withAdditionalFieldNames(String...)} for project-specific fields.
 */
public final class SensitiveDataMasker {

    private static final String REDACTED = "***";

    private static final Set<String> DEFAULT_FIELD_NAMES = Set.of(
        "authorization", "password", "pass", "newpassword", "clave", "secret", "clientsecret",
        "token", "accesstoken", "access_token", "apikey", "api-key", "api_key", "certificatepassword",
        "connection-string", "cookie", "set-cookie",
        "cvv", "cvv2", "cardnumber", "card_number", "numerotarjeta",
        "dni", "cuil", "cuit", "cbu"
    );

    private final Set<String> fieldNames;
    private final Set<String> normalizedFieldNames;
    private final Pattern bodyPattern;

    private SensitiveDataMasker(Set<String> fieldNames) {
        this.fieldNames = Set.copyOf(fieldNames);
        this.normalizedFieldNames = fieldNames.stream()
            .map(SensitiveDataMasker::normalize)
            .collect(Collectors.toUnmodifiableSet());
        this.bodyPattern = buildBodyPattern(this.fieldNames);
    }

    public static SensitiveDataMasker defaultMasker() {
        return new SensitiveDataMasker(DEFAULT_FIELD_NAMES);
    }

    /** Returns a new masker with {@code names} added to the default field name list. */
    public SensitiveDataMasker withAdditionalFieldNames(String... names) {
        Set<String> merged = new LinkedHashSet<>(fieldNames);
        for (String name : names) {
            merged.add(name);
        }
        return new SensitiveDataMasker(merged);
    }

    /** Redacts any header whose name matches a configured field name (case/separator-insensitive). */
    public Map<String, String> maskHeaders(HttpHeaders headers) {
        Map<String, String> masked = new LinkedHashMap<>();
        headers.map().forEach((name, values) ->
            masked.put(name, isSensitiveHeader(name) ? REDACTED : String.join(",", values)));
        return masked;
    }

    /** Redacts the value of any {@code "fieldName": value} pair whose key matches a configured field name. */
    public String maskBody(String body) {
        if (body == null || body.isEmpty() || fieldNames.isEmpty()) {
            return body;
        }
        return bodyPattern.matcher(body).replaceAll("$1\"" + REDACTED + "\"");
    }

    private boolean isSensitiveHeader(String name) {
        return normalizedFieldNames.contains(normalize(name));
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static Pattern buildBodyPattern(Set<String> fieldNames) {
        String alternation = fieldNames.stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
        return Pattern.compile(
            "(\"(?:" + alternation + ")\"\\s*:\\s*)(\"[^\"]*\"|-?[0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    }
}
