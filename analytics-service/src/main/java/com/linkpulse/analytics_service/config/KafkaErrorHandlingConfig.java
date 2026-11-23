package com.linkpulse.analytics_service.config;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.linkpulse.analytics_service.event.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@Configuration
public class KafkaErrorHandlingConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

	// WHY: a dedicated producer is needed because this service otherwise only consumes
	@Bean
	ProducerFactory<String, Object> dlqProducerFactory(
			@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
		// WHY: deserialization failures arrive as raw bytes, valid-but-failing records as ClickEvent objects
		Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
		byType.put(byte[].class, new ByteArraySerializer());
		byType.put(Object.class, new JacksonJsonSerializer<Object>());
		// WHY: assignable=true lets ClickEvent match the Object entry
		DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(byType, true);
		return new DefaultKafkaProducerFactory<>(
				Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
				new StringSerializer(), valueSerializer);
	}

	@Bean
	KafkaTemplate<String, Object> dlqKafkaTemplate(ProducerFactory<String, Object> dlqProducerFactory) {
		return new KafkaTemplate<>(dlqProducerFactory);
	}

	// WHY: Boot attaches a CommonErrorHandler bean to the listener container automatically
	@Bean
	CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dlqKafkaTemplate, MeterRegistry registry,
			@Value("${app.click-events.dlq-topic}") String dlqTopic) {
		// WHY: same partition number keeps a link's failed clicks in order on the DLQ too
		DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
				(record, ex) -> new TopicPartition(dlqTopic, record.partition()));

		Counter dlqCounter = registry.counter("linkpulse.analytics.dlq");
		ConsumerRecordRecoverer recoverer = (record, ex) -> {
			Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
			// WHY: value is null for deserialization failures, but the key is always the short code
			String code = record.value() instanceof ClickEvent event ? event.code() : record.key() + "";
			log.warn("Sending to DLQ: code={} partition={} offset={} reason={}",
					code, record.partition(), record.offset(), cause.toString());
			dlqCounter.increment();
			publisher.accept(record, ex);
		};

		// WHY: 1s, 2s, 4s then give up, so transient DB blips heal without hammering the database
		ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
		backOff.setInitialInterval(1000L);
		backOff.setMultiplier(2.0);

		DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
		// WHY: bad data will fail identically every time, so retrying only delays the partition
		handler.addNotRetryableExceptions(IllegalArgumentException.class, DateTimeParseException.class,
				DeserializationException.class);
		return handler;
	}
}
