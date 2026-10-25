package com.backend.home_monitoring_hub.reading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReadingMessage(
        OffsetDateTime timestamp,
        Double temperature,
        Double humidity,
        Double light,
        Double pressure,
        Double altitude
) {
    public Reading toEntity() {
        Reading reading = new Reading();
        reading.setTimestamp(timestamp.toInstant());
        reading.setTemperature(temperature);
        reading.setHumidity(humidity);
        reading.setLight(light);
        reading.setPressure(pressure);
        reading.setAltitude(altitude);
        return reading;
    }
}