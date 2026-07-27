package com.plug.http.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    @Test
    void opensAfterConsecutiveFailuresReachThreshold() {
        CircuitBreaker breaker = new CircuitBreaker(
            CircuitBreakerConfig.builder().failureThreshold(3).openDuration(Duration.ofMinutes(1)).build());

        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();
        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();
        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();

        assertFalse(breaker.tryAcquire());
    }

    @Test
    void halfOpenTrialAfterOpenDurationElapses() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(
            CircuitBreakerConfig.builder().failureThreshold(1).openDuration(Duration.ofMillis(20)).build());

        breaker.recordFailure();
        assertFalse(breaker.tryAcquire());

        Thread.sleep(50);

        assertTrue(breaker.tryAcquire());
    }

    @Test
    void closesAfterSuccessfulHalfOpenTrial() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(
            CircuitBreakerConfig.builder().failureThreshold(1).openDuration(Duration.ofMillis(20)).build());

        breaker.recordFailure();
        Thread.sleep(50);
        assertTrue(breaker.tryAcquire());
        breaker.recordSuccess();

        assertTrue(breaker.tryAcquire());
        assertTrue(breaker.tryAcquire());
    }

    @Test
    void reopensOnFailedHalfOpenTrial() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(
            CircuitBreakerConfig.builder().failureThreshold(1).openDuration(Duration.ofMillis(20)).build());

        breaker.recordFailure();
        Thread.sleep(50);
        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();

        assertFalse(breaker.tryAcquire());
    }
}
