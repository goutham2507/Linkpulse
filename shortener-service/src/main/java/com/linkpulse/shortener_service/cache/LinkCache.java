package com.linkpulse.shortener_service.cache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LinkCache {

    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);
    private static final String PREFIX = "link:";   // namespaces our keys in a shared Redis

    private final StringRedisTemplate redis;
    public static final String NOT_FOUND = "__NOT_FOUND__";   // can't collide with a URL, which always has a scheme

    private final Duration ttl;
    private final Duration negativeTtl;
    private final MeterRegistry registry;
    private final boolean enabled;   // off switch so benchmarks can compare cache OFF vs ON

    public LinkCache(StringRedisTemplate redis,
                     MeterRegistry registry,
                     @Value("${app.cache.link-ttl-seconds}") long ttlSeconds,
                     @Value("${app.cache.negative-ttl-seconds:60}") long negativeTtlSeconds,
                     @Value("${app.cache.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.registry = registry;
        this.enabled = enabled;
        log.info("Link cache enabled={}", enabled);
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.negativeTtl = Duration.ofSeconds(negativeTtlSeconds);
        // a gauge because the ratio is derived on demand from the counters, not accumulated
        Gauge.builder("linkpulse.cache.hit.ratio", this, LinkCache::hitRatio).register(registry);
    }

    private void count(String result) {
        // one counter name with a result tag, so a dashboard can slice hit/miss/error from a single metric
        registry.counter("linkpulse.cache.lookups", "result", result).increment();
    }

    double hitRatio() {
        double hits = registry.counter("linkpulse.cache.lookups", "result", "hit").count()
                + registry.counter("linkpulse.cache.lookups", "result", "negative_hit").count();
        double total = hits
                + registry.counter("linkpulse.cache.lookups", "result", "miss").count()
                + registry.counter("linkpulse.cache.lookups", "result", "error").count();
        return total == 0 ? 0.0 : hits / total;   // avoid NaN before the first lookup
    }

    public Optional<String> get(String code) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String value = redis.opsForValue().get(PREFIX + code);
            String result = value == null ? "miss" : NOT_FOUND.equals(value) ? "negative_hit" : "hit";
            count(result);
            log.debug("{} {}", value == null ? "MISS" : NOT_FOUND.equals(value) ? "NEGATIVE HIT" : "HIT", code);
            return Optional.ofNullable(value);
        } catch (DataAccessException e) {
            count("error");   // errors count against the ratio, so a Redis outage shows up as a drop
            log.warn("Redis get failed for {}, falling back to DB: {}", code, e.getMessage());
            return Optional.empty();   // outage degrades to a cache miss, never a failed redirect
        }
    }

    public void put(String code, String longUrl, LocalDateTime expiresAt) {
        if (!enabled) {
            return;
        }
        try {
            Duration effective = ttlFor(expiresAt);
            if (effective.compareTo(Duration.ofSeconds(1)) < 0) {
                return;   // Redis rejects non-positive TTLs, and a sub-second entry is not worth caching
            }
            redis.opsForValue().set(PREFIX + code, longUrl, effective);
        } catch (DataAccessException e) {
            log.warn("Redis put failed for {}: {}", code, e.getMessage());
        }
    }

    public void putNotFound(String code) {
        if (!enabled) {
            return;
        }
        try {
            // short TTL bounds how long a wrongly-negative entry can linger if an eviction is missed
            redis.opsForValue().set(PREFIX + code, NOT_FOUND, negativeTtl);
            log.debug("Cache NEGATIVE PUT for code={}", code);
        } catch (DataAccessException e) {
            log.warn("Redis negative put failed for {}: {}", code, e.getMessage());
        }
    }

    Duration ttlFor(LocalDateTime expiresAt) {
        // jitter (90-100%) spreads expiries so entries cached together don't all miss together
        long jittered = (long) (ttl.getSeconds() * ThreadLocalRandom.current().nextDouble(0.9, 1.0));
        Duration effective = Duration.ofSeconds(jittered);
        if (expiresAt != null) {
            // never cache past expiry, or an expired link would keep redirecting
            Duration untilExpiry = Duration.between(LocalDateTime.now(), expiresAt);
            if (untilExpiry.compareTo(effective) < 0) {
                effective = untilExpiry;
            }
        }
        return effective;
    }

    public void evictAfterCommit(String code) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // evicting before commit lets a concurrent reader re-cache the old DB state
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(code);
                }
            });
        } else {
            evict(code);
        }
    }

    public void evict(String code) {
        try {
            redis.delete(PREFIX + code);   // delete rather than update: the next read reloads the truth from the DB
            log.debug("Cache EVICT for code={}", code);
        } catch (DataAccessException e) {
            log.warn("Redis evict failed for {}: {}", code, e.getMessage());
        }
    }
}
