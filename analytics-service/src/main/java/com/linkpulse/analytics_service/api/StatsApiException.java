package com.linkpulse.analytics_service.api;

import org.springframework.http.HttpStatus;

/** An error the stats API deliberately reports to the caller with a specific HTTP status. */
public class StatsApiException extends RuntimeException {

	private final HttpStatus status;

	public StatsApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
