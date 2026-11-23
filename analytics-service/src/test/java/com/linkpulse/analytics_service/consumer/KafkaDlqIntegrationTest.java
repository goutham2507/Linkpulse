package com.linkpulse.analytics_service.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class KafkaDlqIntegrationTest {

	// WHY: real Postgres and Kafka prove the whole path: consumer, error handler, DLQ publisher
	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Container
	@ServiceConnection
	static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

	// WHY: the DLQ producer reads this property directly, and @ServiceConnection alone doesn't set it
	@DynamicPropertySource
	static void kafkaProps(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
	}

	// WHY: mirror kafka-init (3 partitions) and create topics before the app starts, so no consumer subscribes to a missing topic
	@BeforeAll
	static void createTopics() throws Exception {
		try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
			admin.createTopics(List.of(new NewTopic("click.events", 3, (short) 1),
					new NewTopic("click.events.dlq", 3, (short) 1))).all().get();
		}
	}

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MeterRegistry registry;

	@Test
	void nonJsonMessage_goesToDlq() {
		byte[] garbage = "this is not json{".getBytes();
		try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(
				Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
				new StringSerializer(), new org.apache.kafka.common.serialization.ByteArraySerializer())) {
			producer.send(new ProducerRecord<>("click.events", "bad-code", garbage));
			producer.flush();
		}

		ConsumerRecord<String, byte[]> dead = awaitDlqRecord("bad-code");

		// WHY: the original bytes must survive so the message can be inspected or replayed later
		assertThat(dead.value()).isEqualTo(garbage);
		assertThat(registry.counter("linkpulse.analytics.dlq").count()).isGreaterThanOrEqualTo(1.0);
	}

	@Test
	void validEvent_isStored() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String json = "{\"eventId\":\"" + eventId + "\",\"code\":\"ok-code\",\"occurredAt\":\""
				+ Instant.now() + "\",\"device\":\"mobile\",\"referrer\":null}";
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(
				Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
				new StringSerializer(), new StringSerializer())) {
			producer.send(new ProducerRecord<>("click.events", "ok-code", json));
			producer.flush();
		}

		// WHY: the listener is asynchronous, so poll until the row appears instead of sleeping a fixed time
		long deadline = System.currentTimeMillis() + 30_000;
		Long count = 0L;
		while (System.currentTimeMillis() < deadline) {
			count = jdbc.queryForObject("SELECT count(*) FROM click_events WHERE event_id = ?::uuid", Long.class, eventId);
			if (count != null && count == 1L) {
				break;
			}
			Thread.sleep(200);
		}
		assertThat(count).isEqualTo(1L);
	}

	private ConsumerRecord<String, byte[]> awaitDlqRecord(String key) {
		try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
				new StringDeserializer(), new ByteArrayDeserializer())) {
			consumer.subscribe(List.of("click.events.dlq"));
			long deadline = System.currentTimeMillis() + 30_000;
			while (System.currentTimeMillis() < deadline) {
				for (ConsumerRecord<String, byte[]> r : consumer.poll(Duration.ofMillis(500))) {
					if (key.equals(r.key())) {
						return r;
					}
				}
			}
		}
		throw new AssertionError("no record with key " + key + " reached click.events.dlq within 30s");
	}
}
