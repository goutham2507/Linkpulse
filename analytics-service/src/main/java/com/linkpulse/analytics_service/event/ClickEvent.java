package com.linkpulse.analytics_service.event;

// WHY: mirrors the JSON the shortener publishes; occurredAt stays a String to avoid date-format coupling for now
public record ClickEvent(String eventId, String code, String occurredAt, String device, String referrer) {
}
