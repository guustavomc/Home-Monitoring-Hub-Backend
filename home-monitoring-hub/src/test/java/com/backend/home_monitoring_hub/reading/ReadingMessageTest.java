package com.backend.home_monitoring_hub.reading;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingMessageTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void convertsOffsetTimestampToUtc() {
        ReadingMessage message = objectMapper.readValue(
                "{\"timestamp\":\"2026-10-25T18:40:39-03:00\",\"temperature\":24.5}",
                ReadingMessage.class);

        Reading reading = message.toEntity();

        assertThat(reading.getTimestamp()).isEqualTo(Instant.parse("2026-10-25T21:40:39Z"));
        assertThat(reading.getTemperature()).isEqualTo(24.5);
    }

    @Test
    void keepsNullSensorValues() {
        ReadingMessage message = objectMapper.readValue(
                "{\"timestamp\":\"2026-10-25T18:40:39-03:00\",\"temperature\":24.5,\"humidity\":null}",
                ReadingMessage.class);

        Reading reading = message.toEntity();

        assertThat(reading.getHumidity()).isNull();
        assertThat(reading.getLight()).isNull();   // missing field
    }

    @Test
    void ignoresUnknownFields() {
        ReadingMessage message = objectMapper.readValue(
                "{\"timestamp\":\"2026-10-25T18:40:39-03:00\",\"motion\":true}",
                ReadingMessage.class);

        assertThat(message.timestamp()).isNotNull();
    }
}