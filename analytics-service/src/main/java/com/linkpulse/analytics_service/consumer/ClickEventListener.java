package com.linkpulse.analytics_service.consumer;

import com.linkpulse.analytics_service.event.ClickEvent;
import com.linkpulse.analytics_service.service.ClickRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClickEventListener {

	private static final Logger log = LoggerFactory.getLogger(ClickEventListener.class);

	private final ClickRecorder recorder;
	private final String serverPort;

	public ClickEventListener(ClickRecorder recorder, @Value("${server.port}") String serverPort) {
		this.recorder = recorder;
		this.serverPort = serverPort;
	}

	// WHY: topic name comes from config so it matches the shortener's producer setting
	@KafkaListener(topics = "${app.click-events.topic}")
	public void onClick(ConsumerRecord<String, ClickEvent> record) {
		ClickEvent e = record.value();
		// WHY: store first, log after, so the log line only claims success once the DB commit has happened
		recorder.record(e);
		// WHY: port in the tag shows which analytics instance handled the click; partition/offset show ordering and replay
		log.info("[analytics-{}] Click stored: code={} device={} | partition={} offset={}",
				serverPort, e.code(), e.device(), record.partition(), record.offset());
	}
}
