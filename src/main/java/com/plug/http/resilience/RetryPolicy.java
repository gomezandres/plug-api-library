package com.plug.http.resilience;

import java.time.Duration;

/** Decides whether a failed attempt should be retried, and how long to wait before the next one. */
public interface RetryPolicy {

    /**
     * @param method     the HTTP method of the request
     * @param attempt    the attempt number that just completed (1-based)
     * @param statusCode the response status code, or -1 if no response was received
     * @param error      the failure cause, or null if the attempt returned a response
     */
    boolean shouldRetry(String method, int attempt, int statusCode, Throwable error);

    Duration backoff(int attempt);
}
