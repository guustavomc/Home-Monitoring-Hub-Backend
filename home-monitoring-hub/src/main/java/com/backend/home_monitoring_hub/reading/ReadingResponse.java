package com.backend.home_monitoring_hub.reading;

import java.time.Instant;

public record ReadingResponse(
        Long id,
        Instant timestamp,
        Double temperature,
        Double humidity,
        Double light,
        Double pressure,
        Double altitude
) 
{
    public static ReadingResponse from(Reading reading) {
        return new ReadingResponse(
                reading.getId(),
                reading.getTimestamp(),
                reading.getTemperature(),
                reading.getHumidity(),
                reading.getLight(),
                reading.getPressure(),
                reading.getAltitude()
        );
    }
}