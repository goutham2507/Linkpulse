package com.linkpulse.analytics_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// WHY: don't start the Kafka listener so the test needs no running broker
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class AnalyticsServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
