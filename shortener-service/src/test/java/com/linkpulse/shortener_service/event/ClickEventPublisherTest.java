package com.linkpulse.shortener_service.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class ClickEventPublisherTest {

    private static final String IPHONE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148";

    @Mock
    private KafkaTemplate<String, ClickEvent> kafkaTemplate;

    @Test
    void publish_sendsEventKeyedByCode() {
        when(kafkaTemplate.send(anyString(), anyString(), any(ClickEvent.class)))
                .thenReturn(new CompletableFuture<>());
        ClickEventPublisher publisher = new ClickEventPublisher(kafkaTemplate, "click.events", true);

        publisher.publish("abc", IPHONE_UA, "https://www.google.com/search?q=shoes");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("click.events"),
                org.mockito.ArgumentMatchers.eq("abc"), captor.capture());   // key = code

        ClickEvent event = captor.getValue();
        assertEquals("abc", event.code());
        assertEquals("mobile", event.device());
        assertEquals("www.google.com", event.referrer());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
    }

    @Test
    void publish_whenKafkaThrows_doesNotBreakRedirect() {
        when(kafkaTemplate.send(anyString(), anyString(), any(ClickEvent.class)))
                .thenThrow(new TimeoutException("metadata not available"));
        ClickEventPublisher publisher = new ClickEventPublisher(kafkaTemplate, "click.events", true);

        assertDoesNotThrow(() -> publisher.publish("abc", IPHONE_UA, null));
    }

    @Test
    void publish_whenDisabled_sendsNothing() {
        ClickEventPublisher publisher = new ClickEventPublisher(kafkaTemplate, "click.events", false);

        publisher.publish("abc", IPHONE_UA, null);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(ClickEvent.class));
    }

    @Test
    void deviceFrom_detectsCommonDevices() {
        assertEquals("mobile", ClickEventPublisher.deviceFrom(IPHONE_UA));
        assertEquals("tablet", ClickEventPublisher.deviceFrom("Mozilla/5.0 (iPad; CPU OS 17_0)"));
        assertEquals("bot", ClickEventPublisher.deviceFrom("Googlebot/2.1"));
        assertEquals("desktop", ClickEventPublisher.deviceFrom("PostmanRuntime/7.39.0"));
        assertEquals("unknown", ClickEventPublisher.deviceFrom(null));
    }

    @Test
    void referrerHost_keepsOnlyTheSiteName() {
        assertEquals("www.google.com", ClickEventPublisher.referrerHost("https://www.google.com/search?q=x"));
        assertNull(ClickEventPublisher.referrerHost(null));
        assertNull(ClickEventPublisher.referrerHost("not a url ::"));
    }
}