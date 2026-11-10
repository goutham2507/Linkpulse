package com.linkpulse.shortener_service.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        rateLimiter = new RateLimiter(redis, meters);
    }

    @Test
    void firstRequest_isAllowed_andStartsTimer() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        RateLimitResult result = rateLimiter.tryAcquire("create", "user:1", 10, WINDOW);

        assertTrue(result.allowed());
        verify(redis).expire(anyString(), eq(WINDOW));
    }

    @Test
    void tenthRequest_isAllowed() {
        when(valueOps.increment(anyString())).thenReturn(10L);

        RateLimitResult result = rateLimiter.tryAcquire("create", "user:1", 10, WINDOW);

        assertTrue(result.allowed());
        verify(redis, never()).expire(anyString(), eq(WINDOW));   // only the first hit starts the timer
    }

    @Test
    void eleventhRequest_isBlocked_withRetryAfter() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        RateLimitResult result = rateLimiter.tryAcquire("create", "user:1", 10, WINDOW);

        assertFalse(result.allowed());
        assertTrue(result.retryAfterSeconds() >= 1 && result.retryAfterSeconds() <= 60);
    }

    @Test
    void redisDown_failsOpen() {
        when(valueOps.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        RateLimitResult result = rateLimiter.tryAcquire("create", "user:1", 10, WINDOW);

        assertEquals(true, result.allowed());
    }

    @Test
    void blockedRequest_incrementsRejectedMetric() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        rateLimiter.tryAcquire("create", "user:1", 10, WINDOW);

        assertEquals(1.0, meters.get("linkpulse.ratelimit.rejected").tag("bucket", "create").counter().count());
    }
}
