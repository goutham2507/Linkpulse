package com.linkpulse.analytics_service;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AnalyticsServiceApplication {

	public static void main(String[] args) {
		// WHY: fix the default zone before Spring starts so logs/timestamps are consistently IST
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(AnalyticsServiceApplication.class, args);
	}

}
