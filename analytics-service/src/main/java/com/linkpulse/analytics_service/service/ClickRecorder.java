package com.linkpulse.analytics_service.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.linkpulse.analytics_service.event.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClickRecorder {

	private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

	private final JdbcTemplate jdbc;
	private final ZoneId statsZone;
	private final Counter duplicates;

	public ClickRecorder(JdbcTemplate jdbc, @Value("${app.stats.zone}") ZoneId statsZone, MeterRegistry registry) {
		this.jdbc = jdbc;
		this.statsZone = statsZone;
		// WHY: a metric makes the redelivery rate visible without digging through DEBUG logs
		this.duplicates = registry.counter("linkpulse.analytics.duplicates");
	}

	// WHY: one transaction so the raw row and the daily total can never disagree
	@Transactional
	public void record(ClickEvent event) {
		// WHY: reject bad events before touching the DB so they fail fast and leave no partial writes
		if (event == null || event.eventId() == null || event.code() == null || event.occurredAt() == null) {
			throw new IllegalArgumentException("eventId, code and occurredAt are required");
		}
		UUID eventId;
		Instant occurredAt;
		try {
			eventId = UUID.fromString(event.eventId());
			occurredAt = Instant.parse(event.occurredAt());
		} catch (IllegalArgumentException | DateTimeParseException ex) {
			throw new IllegalArgumentException("eventId or occurredAt is not parseable", ex);
		}

		// WHY: the column is NOT NULL, so a missing device is stored as "unknown" (the shortener's own fallback value)
		String device = event.device() == null ? "unknown" : event.device();

		// WHY: Kafka delivers at-least-once, so the UNIQUE event_id turns a redelivery into a no-op instead of an error
		int inserted = jdbc.update(
				"INSERT INTO click_events (event_id, code, occurred_at, device, referrer, received_at) VALUES (?, ?, ?, ?, ?, ?) "
						+ "ON CONFLICT (event_id) DO NOTHING",
				eventId, event.code(), Timestamp.from(occurredAt), device, event.referrer(), Timestamp.from(Instant.now()));
		if (inserted == 0) {
			// WHY: 0 rows means already stored; skipping the daily upsert keeps the totals exact
			log.debug("Duplicate click ignored: eventId={} code={}", eventId, event.code());
			duplicates.increment();
			return;
		}

		// WHY: day is computed in the stats zone so clicks late in the evening count for the local calendar day
		LocalDate day = LocalDate.ofInstant(occurredAt, statsZone);
		// WHY: single atomic upsert avoids the read-then-write race between concurrent consumers
		jdbc.update(
				"INSERT INTO daily_link_stats (code, day, clicks) VALUES (?, ?, 1) "
						+ "ON CONFLICT (code, day) DO UPDATE SET clicks = daily_link_stats.clicks + 1",
				event.code(), day);
	}
}
