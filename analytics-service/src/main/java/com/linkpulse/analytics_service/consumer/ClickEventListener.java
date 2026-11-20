package com.linkpulse.analytics_service.consumer;

import com.linkpulse.analytics_service.event.ClickEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClickEventListener {

	private static final Logger log = LoggerFactory.getLogger(ClickEventListener.class);

	// WHY: topic name comes from config so it matches the shortener's producer setting
	@KafkaListener(topics = "${app.click-events.topic}")
	public void onClick(ConsumerRecord<String, ClickEvent> record) {
		ClickEvent e = record.value();
		// WHY: partition/offset in the log make it easy to verify ordering and replay behaviour
		log.info("click code={} device={} referrer={} partition={} offset={} eventId={}",
				e.code(), e.device(), e.referrer(), record.partition(), record.offset(), e.eventId());
	}
}
