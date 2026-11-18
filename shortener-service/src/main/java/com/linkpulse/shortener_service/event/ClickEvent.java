package com.linkpulse.shortener_service.event;

/** One click on a short link. Sent to Kafka as JSON. */
public record ClickEvent(
        String eventId,      // unique per click → lets the consumer drop duplicates (Day 37)
        String code,         // which link; also the Kafka key → same partition, in order
        String occurredAt,   // ISO-8601 UTC timestamp, e.g. 2026-11-18T03:10:00.123Z
        String device,       // mobile | tablet | desktop | bot | unknown
        String referrer) {   // referring site's host only (no full URL, no IP)
}