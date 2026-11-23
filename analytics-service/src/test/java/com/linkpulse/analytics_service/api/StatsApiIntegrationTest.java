package com.linkpulse.analytics_service.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
// WHY: don't start the Kafka listener so the test needs no running broker
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
class StatsApiIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	// WHY: ownership comes from shortener-service, so the test scripts its answers instead of running a second app
	@MockitoBean
	ShortenerClient shortener;

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM daily_link_stats");
		insert("mine1", today, 4);
		insert("mine1", today.minusDays(2), 6);
		insert("mine2", today, 5);
		insert("other", today, 99); // WHY: belongs to someone else, so it must never appear in my totals
	}

	@Test
	void linkStats_fillsMissingDaysWithZero() throws Exception {
		mvc.perform(get("/v1/stats/mine1?days=3").header("Authorization", "Bearer t"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("mine1"))
				.andExpect(jsonPath("$.totalClicks").value(10))
				.andExpect(jsonPath("$.daily.length()").value(3))
				.andExpect(jsonPath("$.daily[0].clicks").value(6))
				.andExpect(jsonPath("$.daily[1].clicks").value(0))
				.andExpect(jsonPath("$.daily[2].clicks").value(4));
	}

	@Test
	void linkStats_forSomeoneElsesLink_returns404() throws Exception {
		doThrow(new StatsApiException(HttpStatus.NOT_FOUND, "Link not found"))
				.when(shortener).requireOwner(eq("other"), any());

		mvc.perform(get("/v1/stats/other").header("Authorization", "Bearer t"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Link not found"));
	}

	@Test
	void linkStats_withoutToken_returns401() throws Exception {
		doThrow(new StatsApiException(HttpStatus.UNAUTHORIZED, "Authentication required"))
				.when(shortener).requireOwner(eq("mine1"), any());

		mvc.perform(get("/v1/stats/mine1")).andExpect(status().isUnauthorized());
	}

	@Test
	void linkStats_withMalformedCode_returns404() throws Exception {
		mvc.perform(get("/v1/stats/bad$code").header("Authorization", "Bearer t"))
				.andExpect(status().isNotFound());
	}

	@Test
	void summary_countsOnlyMyLinks_andRanksTopLinks() throws Exception {
		when(shortener.myCodes(any())).thenReturn(List.of("mine1", "mine2"));

		mvc.perform(get("/v1/stats/summary?days=7").header("Authorization", "Bearer t"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalLinks").value(2))
				.andExpect(jsonPath("$.totalClicks").value(15))
				.andExpect(jsonPath("$.topLinks[0].code").value("mine1"))
				.andExpect(jsonPath("$.topLinks[0].clicks").value(10))
				.andExpect(jsonPath("$.topLinks[1].code").value("mine2"));
	}

	@Test
	void summary_forUserWithNoLinks_isAllZero() throws Exception {
		when(shortener.myCodes(any())).thenReturn(List.of());

		mvc.perform(get("/v1/stats/summary").header("Authorization", "Bearer t"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalLinks").value(0))
				.andExpect(jsonPath("$.totalClicks").value(0))
				.andExpect(jsonPath("$.daily.length()").value(30));
	}

	@Test
	void days_isClampedTo90() throws Exception {
		mvc.perform(get("/v1/stats/mine1?days=100000").header("Authorization", "Bearer t"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.daily.length()").value(90));
	}

	private void insert(String code, LocalDate day, long clicks) {
		jdbc.update("INSERT INTO daily_link_stats (code, day, clicks) VALUES (?, ?, ?)", code, Date.valueOf(day), clicks);
	}
}
