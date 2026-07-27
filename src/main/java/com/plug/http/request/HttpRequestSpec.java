package com.plug.http.request;

import com.plug.http.client.PlugHttpClient;
import com.plug.http.exception.PlugHttpException;
import com.plug.http.response.PlugHttpResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fluent, per-call request builder returned by {@code PlugHttpClient.get/post/put/delete/patch}.
 * Build one, configure it, then call one of the {@code execute*} methods.
 */
public final class HttpRequestSpec {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    private final PlugHttpClient client;
    private final String method;
    private final String path;
    private final Map<String, Object> pathParams = new LinkedHashMap<>();
    private final Map<String, List<String>> queryParams = new LinkedHashMap<>();
    private final Map<String, String> requestHeaders = new LinkedHashMap<>();
    private Object body;
    private boolean throwOnError = true;

    /** Not intended for direct use — obtain instances via {@code PlugHttpClient}. */
    public HttpRequestSpec(PlugHttpClient client, String method, String path) {
        this.client = client;
        this.method = method;
        this.path = path;
    }

    public HttpRequestSpec pathParam(String name, Object value) {
        pathParams.put(name, value);
        return this;
    }

    public HttpRequestSpec queryParam(String name, Object value) {
        queryParams.computeIfAbsent(name, key -> new ArrayList<>()).add(String.valueOf(value));
        return this;
    }

    public HttpRequestSpec header(String name, String value) {
        requestHeaders.put(name, value);
        return this;
    }

    /**
     * Sets the request body. {@code byte[]} and {@code String} are sent as-is; any other
     * object is serialized to JSON via the client's configured {@code JsonCodec}.
     */
    public HttpRequestSpec body(Object body) {
        this.body = body;
        return this;
    }

    /** Defaults to true: a non-2xx response throws {@code HttpStatusException}. */
    public HttpRequestSpec throwOnError(boolean throwOnError) {
        this.throwOnError = throwOnError;
        return this;
    }

    public <T> T execute(Class<T> responseType) {
        return join(executeAsync(responseType));
    }

    public <T> CompletableFuture<T> executeAsync(Class<T> responseType) {
        return client.executeInternal(this, responseType).thenApply(PlugHttpResponse::body);
    }

    public PlugHttpResponse<String> executeString() {
        return join(executeStringAsync());
    }

    public CompletableFuture<PlugHttpResponse<String>> executeStringAsync() {
        return client.executeInternal(this, String.class);
    }

    public PlugHttpResponse<Void> execute() {
        return join(executeAsync());
    }

    public CompletableFuture<PlugHttpResponse<Void>> executeAsync() {
        return client.executeInternal(this, Void.class);
    }

    public String method() {
        return method;
    }

    public Object rawBody() {
        return body;
    }

    public boolean throwOnError() {
        return throwOnError;
    }

    public Map<String, String> headers() {
        return Map.copyOf(requestHeaders);
    }

    public URI resolvedUri(URI baseUri) {
        String resolvedPath = resolvePath();
        String query = buildQueryString();
        String base = baseUri.toString();
        StringBuilder result = new StringBuilder();
        if (base.endsWith("/") && resolvedPath.startsWith("/")) {
            result.append(base, 0, base.length() - 1).append(resolvedPath);
        } else if (!base.endsWith("/") && !resolvedPath.startsWith("/")) {
            result.append(base).append('/').append(resolvedPath);
        } else {
            result.append(base).append(resolvedPath);
        }
        if (!query.isEmpty()) {
            result.append('?').append(query);
        }
        return URI.create(result.toString());
    }

    private String resolvePath() {
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(path, last, matcher.start());
            String name = matcher.group(1);
            Object value = pathParams.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing path param '" + name + "' for path " + path);
            }
            result.append(UriEncoding.encodePathSegment(String.valueOf(value)));
            last = matcher.end();
        }
        result.append(path, last, path.length());
        return result.toString();
    }

    private String buildQueryString() {
        return queryParams.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                .map(value -> UriEncoding.encodeQueryComponent(entry.getKey()) + "=" + UriEncoding.encodeQueryComponent(value)))
            .collect(Collectors.joining("&"));
    }

    private static <R> R join(CompletableFuture<R> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new PlugHttpException("HTTP request failed", cause != null ? cause : e);
        }
    }
}
