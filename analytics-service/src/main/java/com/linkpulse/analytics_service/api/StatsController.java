package com.linkpulse.analytics_service.api;

import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stats")
public class StatsController {

	private static final Pattern VALID_CODE = Pattern.compile("^[a-zA-Z0-9_-]{1,20}$");
	private static final int MAX_DAYS = 90;

	private final StatsService stats;
	private final ShortenerClient shortener;

	public StatsController(StatsService stats, ShortenerClient shortener) {
		this.stats = stats;
		this.shortener = shortener;
	}

	// WHY: the literal "summary" wins over "{code}", so a link can't shadow it
	@GetMapping("/summary")
	public StatsService.Summary summary(@RequestParam(defaultValue = "30") int days,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return stats.summary(shortener.myCodes(authorization), clamp(days));
	}

	@GetMapping("/{code}")
	public StatsService.LinkStats forLink(@PathVariable String code, @RequestParam(defaultValue = "30") int days,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		// WHY: reject malformed codes before calling the shortener; real codes can't look like this
		if (!VALID_CODE.matcher(code).matches()) {
			throw new StatsApiException(HttpStatus.NOT_FOUND, "Link not found");
		}
		shortener.requireOwner(code, authorization);
		return stats.forLink(code, clamp(days));
	}

	// WHY: bounds the work one request can ask the database for
	private static int clamp(int days) {
		return Math.min(Math.max(days, 1), MAX_DAYS);
	}
}
