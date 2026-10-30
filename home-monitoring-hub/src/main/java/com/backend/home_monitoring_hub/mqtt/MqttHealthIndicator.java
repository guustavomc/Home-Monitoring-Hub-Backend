package com.backend.home_monitoring_hub.mqtt;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

// Shown as "mqtt" in /actuator/health: UP only while subscribed, i.e. while readings can arrive
@Component
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttSubscriber subscriber;

    public MqttHealthIndicator(MqttSubscriber subscriber) {
        this.subscriber = subscriber;
    }

    @Override
    public Health health() {
        Health.Builder builder = subscriber.isSubscribed() ? Health.up() : Health.down();
        return builder
                .withDetail("broker", subscriber.getBrokerUrl())
                .withDetail("topic", subscriber.getTopic())
                .build();
    }
}
