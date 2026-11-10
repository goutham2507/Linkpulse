package com.linkpulse.shortener_service.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkCacheTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SimpleMeterRegistry registry;
    private LinkCache cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        registry = new SimpleMeterRegistry();
        cache = new LinkCache(redis, registry, 3600, 60);
    }

    @Test
    void hitRatio_countsHitsAndMisses() {
        when(valueOps.get("link:a")).thenReturn("https://example.com");
        when(valueOps.get("link:b")).thenReturn(null);

        cache.get("a");
        cache.get("a");
        cache.get("a");
        cache.get("b");

        assertEquals(3.0, registry.counter("linkpulse.cache.lookups", "result", "hit").count());
        assertEquals(1.0, registry.counter("linkpulse.cache.lookups", "result", "miss").count());
        assertEquals(0.75, cache.hitRatio());
    }

    @Test
    void putNotFound_storesMarkerWithShortTtl() {
        cache.putNotFound("ghost");

        verify(valueOps).set("link:ghost", LinkCache.NOT_FOUND, Duration.ofSeconds(60));
    }

    @Test
    void ttl_noExpiry_isAbout54to60Minutes() {
        long seconds = cache.ttlFor(null).getSeconds();

        assertTrue(seconds >= 3240 && seconds <= 3600, "ttl was " + seconds);
    }

    @Test
    void ttl_linkExpiresSoon_isCappedAtExpiry() {
        long seconds = cache.ttlFor(LocalDateTime.now().plusMinutes(10)).getSeconds();

        assertTrue(seconds >= 590 && seconds <= 600, "ttl was " + seconds);
    }

    @Test
    void put_linkAlreadyExpired_isNotCached() {
        cache.put("old", "https://example.com", LocalDateTime.now().minusMinutes(1));

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }
}
