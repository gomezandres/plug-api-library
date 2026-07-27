package com.plug.http.resilience;

import java.time.Duration;

public record CircuitBreakerConfig(
    int failureThreshold,
    Duration openDuration,
    int halfOpenMaxTrialRequests
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int failureThreshold = 5;
        private Duration openDuration = Duration.ofSeconds(30);
        private int halfOpenMaxTrialRequests = 1;

        private Builder() {
        }

        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder openDuration(Duration openDuration) {
            this.openDuration = openDuration;
            return this;
        }

        public Builder halfOpenMaxTrialRequests(int halfOpenMaxTrialRequests) {
            this.halfOpenMaxTrialRequests = halfOpenMaxTrialRequests;
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(failureThreshold, openDuration, halfOpenMaxTrialRequests);
        }
    }
}
