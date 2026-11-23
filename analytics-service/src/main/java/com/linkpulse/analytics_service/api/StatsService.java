package com.linkpulse.analytics_service.api;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

	public record DayCount(LocalDate day, long clicks) {
	}

	public record LinkStats(String code, LocalDate from, LocalDate to, long totalClicks, List<DayCount> daily) {
	}

	public record TopLink(String code, long clicks) {
	}

	public record Summary(LocalDate from, LocalDate to, int totalLinks, long totalClicks, List<DayCount> daily,
			List<TopLink> topLinks) {
	}

	private static final int TOP_LINKS = 5;

	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate named;
	private final ZoneId zone;

	public StatsService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, @Value("${app.stats.zone}") ZoneId zone) {
		this.jdbc = jdbc;
		this.named = named;
		this.zone = zone;
	}

	public LinkStats forLink(String code, int days) {
		LocalDate to = LocalDate.now(zone);
		LocalDate from = to.minusDays(days - 1L);
		Map<LocalDate, Long> byDay = new HashMap<>();
		jdbc.query("SELECT day, clicks FROM daily_link_stats WHERE code = ? AND day BETWEEN ? AND ?",
				rs -> {
					byDay.put(rs.getDate("day").toLocalDate(), rs.getLong("clicks"));
				}, code, Date.valueOf(from), Date.valueOf(to));
		List<DayCount> daily = fill(from, to, byDay);
		return new LinkStats(code, from, to, sum(daily), daily);
	}

	public Summary summary(List<String> codes, int days) {
		LocalDate to = LocalDate.now(zone);
		LocalDate from = to.minusDays(days - 1L);
		if (codes.isEmpty()) {
			// WHY: an empty IN () list is invalid SQL, and a user with no links has nothing to count anyway
			return new Summary(from, to, 0, 0, fill(from, to, Map.of()), List.of());
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("codes", codes).addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));

		Map<LocalDate, Long> byDay = new HashMap<>();
		named.query("SELECT day, SUM(clicks) AS clicks FROM daily_link_stats "
				+ "WHERE code IN (:codes) AND day BETWEEN :from AND :to GROUP BY day", params,
				rs -> {
					byDay.put(rs.getDate("day").toLocalDate(), rs.getLong("clicks"));
				});

		List<TopLink> top = new ArrayList<>();
		// WHY: tie-break on code so the ranking is stable between refreshes
		named.query("SELECT code, SUM(clicks) AS clicks FROM daily_link_stats "
				+ "WHERE code IN (:codes) AND day BETWEEN :from AND :to GROUP BY code "
				+ "ORDER BY clicks DESC, code LIMIT " + TOP_LINKS, params,
				rs -> {
					top.add(new TopLink(rs.getString("code"), rs.getLong("clicks")));
				});

		List<DayCount> daily = fill(from, to, byDay);
		return new Summary(from, to, codes.size(), sum(daily), daily, top);
	}

	// WHY: days with no clicks have no row, but a chart needs every day present as 0
	private static List<DayCount> fill(LocalDate from, LocalDate to, Map<LocalDate, Long> byDay) {
		List<DayCount> out = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			out.add(new DayCount(d, byDay.getOrDefault(d, 0L)));
		}
		return out;
	}

	private static long sum(List<DayCount> daily) {
		return daily.stream().mapToLong(DayCount::clicks).sum();
	}
}
