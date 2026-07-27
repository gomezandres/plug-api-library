package com.plug.http.exception;

/** Thrown immediately, without attempting any network call, when the circuit breaker for a target host is open. */
public class CircuitBreakerOpenException extends PlugHttpException {

    public CircuitBreakerOpenException(String target) {
        super("Circuit breaker is open for target: " + target);
    }
}
