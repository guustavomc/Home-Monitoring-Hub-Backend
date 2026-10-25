package com.backend.home_monitoring_hub.reading;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "readings",
       indexes = @Index(name = "idx_readings_timestamp", columnList = "timestamp"))

public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    private Double temperature;
    private Double humidity;
    private Double light;
    private Double pressure;
    private Double altitude;

    public Long getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public Double getLight() {
        return light;
    }

    public Double getPressure() {
        return pressure;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public void setLight(Double light) {
        this.light = light;
    }

    public void setPressure(Double pressure) {
        this.pressure = pressure;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    
}