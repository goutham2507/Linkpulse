package com.linkpulse.analytics_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.linkpulse.analytics_service.event.ClickEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
// WHY: don't start the Kafka listener so the test needs no running broker
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class ClickRecorderIntegrationTest {

	// WHY: a real Postgres proves the migration and the ON CONFLICT upsert work, which H2 could not
	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	ClickRecorder recorder;

	@Autowired
	JdbcTemplate jdbc;

	// WHY: the real registry proves the counter is actually wired, not just called on a mock
	@Autowired
	MeterRegistry registry;

	// WHY: tests share one container, so start each from empty tables
	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM click_events");
		jdbc.update("DELETE FROM daily_link_stats");
	}

	@Test
	void storesRawClickAndIncrementsDailyTotal() {
		recorder.record(event("abc123", "2026-11-18T03:10:00.123Z"));
		recorder.record(event("abc123", "2026-11-18T05:00:00Z"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM click_events WHERE code = 'abc123'", Long.class))
				.isEqualTo(2L);
		assertThat(jdbc.queryForObject(
				"SELECT clicks FROM daily_link_stats WHERE code = 'abc123' AND day = DATE '2026-11-18'", Long.class))
				.isEqualTo(2L);
	}

	@Test
	void usesIndiaCalendarDay() {
		// WHY: 20:00 UTC is 01:30 IST the next morning, so it must count for the 20th, not the 19th
		recorder.record(event("india1", "2026-11-19T20:00:00Z"));

		assertThat(jdbc.queryForObject(
				"SELECT clicks FROM daily_link_stats WHERE code = 'india1' AND day = DATE '2026-11-20'", Long.class))
				.isEqualTo(1L);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM daily_link_stats WHERE code = 'india1' AND day = DATE '2026-11-19'", Long.class))
				.isZero();
	}

	@Test
	void incompleteEvent_isRejected() {
		ClickEvent noCode = new ClickEvent(UUID.randomUUID().toString(), null, "2026-11-18T03:10:00Z", "mobile", null);
		ClickEvent badTime = new ClickEvent(UUID.randomUUID().toString(), "abc123", "not-a-time", "mobile", null);

		assertThatThrownBy(() -> recorder.record(noCode)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> recorder.record(badTime)).isInstanceOf(IllegalArgumentException.class);
		// WHY: a rejected event must leave nothing behind
		assertThat(jdbc.queryForObject("SELECT count(*) FROM click_events", Long.class)).isZero();
	}

	@Test
	void sameEventTwice_isCountedOnce() {
		ClickEvent e = event("dup1", "2026-11-18T03:10:00Z");

		recorder.record(e);
		recorder.record(e); // WHY: simulates Kafka redelivering the same message

		assertThat(jdbc.queryForObject("SELECT count(*) FROM click_events WHERE code = 'dup1'", Long.class))
				.isEqualTo(1L);
		assertThat(jdbc.queryForObject(
				"SELECT clicks FROM daily_link_stats WHERE code = 'dup1' AND day = DATE '2026-11-18'", Long.class))
				.isEqualTo(1L);
	}

	@Test
	void duplicateIncrementsMetric() {
		ClickEvent e = event("dup2", "2026-11-18T03:10:00Z");
		double before = registry.counter("linkpulse.analytics.duplicates").count();

		recorder.record(e);
		assertThat(registry.counter("linkpulse.analytics.duplicates").count()).isEqualTo(before);
		recorder.record(e);

		assertThat(registry.counter("linkpulse.analytics.duplicates").count()).isEqualTo(before + 1);
	}

	private static ClickEvent event(String code, String occurredAt) {
		return new ClickEvent(UUID.randomUUID().toString(), code, occurredAt, "mobile", "example.com");
	}
}
