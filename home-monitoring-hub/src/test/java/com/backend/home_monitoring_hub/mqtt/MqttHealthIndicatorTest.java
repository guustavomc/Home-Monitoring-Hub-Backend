package com.backend.home_monitoring_hub.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttHealthIndicatorTest {

    @Mock
    private MqttSubscriber subscriber;

    @InjectMocks
    private MqttHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        lenient().when(subscriber.getBrokerUrl()).thenReturn("tcp://localhost:1883");
        lenient().when(subscriber.getTopic()).thenReturn("home/sensors");
    }

    @Test
    void upWhileSubscribed() {
        when(subscriber.isSubscribed()).thenReturn(true);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("broker", "tcp://localhost:1883")
                .containsEntry("topic", "home/sensors");
    }

    @Test
    void downWhenNotSubscribed() {
        when(subscriber.isSubscribed()).thenReturn(false);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
