package com.linkpulse.shortener_service.event;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final KafkaTemplate<String, ClickEvent> kafkaTemplate;
    private final String topic;
    private final boolean enabled;

    public ClickEventPublisher(KafkaTemplate<String, ClickEvent> kafkaTemplate,
                               @Value("${app.click-events.topic:click.events}") String topic,
                               @Value("${app.click-events.enabled:true}") boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.enabled = enabled;
    }

    /** Fire-and-forget: never throws, never waits for Kafka to confirm. */
    public void publish(String code, String userAgent, String refererHeader) {
        if (!enabled) {
            return;
        }
        ClickEvent event = new ClickEvent(
                UUID.randomUUID().toString(),
                code,
                Instant.now().toString(),
                deviceFrom(userAgent),
                referrerHost(refererHeader));

        try {
            kafkaTemplate.send(topic, code, event)              // key = code → same partition per link
                    .whenComplete((result, ex) -> {             // runs later, on a Kafka thread
                        if (ex != null) {
                            log.warn("Click event for {} not delivered: {}", code, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            // e.g. Kafka down and metadata not available within max.block.ms
            log.warn("Kafka unavailable, click event for {} dropped: {}", code, e.getMessage());
        }
    }

    static String deviceFrom(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) {
            return "bot";
        }
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "tablet";
        }
        if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }
        return "desktop";
    }

    static String referrerHost(String refererHeader) {
        if (refererHeader == null || refererHeader.isBlank()) {
            return null;                                         // direct visit (typed / app / QR)
        }
        try {
            return URI.create(refererHeader).getHost();          // keep only the site name
        } catch (IllegalArgumentException e) {
            return null;                                         // malformed header → ignore
        }
    }
}