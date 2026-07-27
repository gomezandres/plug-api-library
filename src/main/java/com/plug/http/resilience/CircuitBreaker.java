package com.plug.http.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal, dependency-free CLOSED / OPEN / HALF_OPEN circuit breaker, one instance per
 * target host. Not a general-purpose resilience toolkit — just enough to stop hammering a
 * target that is clearly down.
 */
public final class CircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final CircuitBreakerConfig config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger halfOpenTrialsInFlight = new AtomicInteger();
    private final AtomicLong openedAtNanos = new AtomicLong();

    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
    }

    /** Call before attempting a request. Returns false if the request must be short-circuited. */
    public boolean tryAcquire() {
        State current = state.get();
        if (current == State.OPEN) {
            long elapsed = System.nanoTime() - openedAtNanos.get();
            if (elapsed >= config.openDuration().toNanos()) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenTrialsInFlight.set(0);
                }
                current = state.get();
            } else {
                return false;
            }
        }
        if (current == State.HALF_OPEN) {
            return halfOpenTrialsInFlight.incrementAndGet() <= config.halfOpenMaxTrialRequests();
        }
        return true;
    }

    public void recordSuccess() {
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
        }
        consecutiveFailures.set(0);
    }

    public void recordFailure() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            open();
            return;
        }
        if (current == State.OPEN) {
            return;
        }
        if (consecutiveFailures.incrementAndGet() >= config.failureThreshold()) {
            open();
        }
    }

    private void open() {
        openedAtNanos.set(System.nanoTime());
        state.set(State.OPEN);
        consecutiveFailures.set(0);
    }
}
