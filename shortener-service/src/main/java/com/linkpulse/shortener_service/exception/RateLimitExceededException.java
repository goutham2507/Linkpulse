package com.linkpulse.shortener_service.exception;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;   // carried so the handler can set the Retry-After header

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many requests, retry in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
