package com.linkpulse.analytics_service.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	// WHY: same JSON shape as shortener-service (timestamp, status, error, message, path), so the frontend parses one format
	@ExceptionHandler(StatsApiException.class)
	public ResponseEntity<Map<String, Object>> handle(StatsApiException ex, HttpServletRequest req) {
		HttpStatus status = ex.status();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now().toString());
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", ex.getMessage());
		body.put("path", req.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
