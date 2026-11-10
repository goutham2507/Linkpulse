package com.linkpulse.shortener_service.ratelimit;

import java.time.Duration;
import java.time.Instant;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String PREFIX = "rate:";   // namespaces our keys in a shared Redis

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    public RateLimiter(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    public RateLimitResult tryAcquire(String bucket, String subject, int limit, Duration window) {
        String key = bucket + ":" + subject;
        long windowSeconds = window.getSeconds();
        long now = Instant.now().getEpochSecond();
        long windowId = now / windowSeconds;   // the window id in the key makes counters reset on their own
        long retryAfter = windowSeconds - (now % windowSeconds);
        String redisKey = PREFIX + bucket + ":" + subject + ":" + windowId;

        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redis.expire(redisKey, window);   // only the first hit sets the TTL, so stale counters clean themselves up
            }
            boolean allowed = count == null || count <= limit;
            if (!allowed) {
                meterRegistry.counter("linkpulse.ratelimit.rejected", "bucket", bucket).increment();   // lets us graph/alert on abuse per endpoint
            }
            return new RateLimitResult(allowed, retryAfter);
        } catch (DataAccessException e) {
            log.warn("Rate limiter Redis failure for {}, failing open: {}", key, e.getMessage());
            return new RateLimitResult(true, retryAfter);   // a Redis outage must not block link creation
        }
    }
}
