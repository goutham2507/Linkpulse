package com.linkpulse.shortener_service.ratelimit;

// retryAfterSeconds is what the 429 Retry-After header needs, so callers never recompute the window
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
}
