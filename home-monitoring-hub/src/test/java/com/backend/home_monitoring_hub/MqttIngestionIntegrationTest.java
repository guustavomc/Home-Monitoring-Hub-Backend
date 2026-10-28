package com.backend.home_monitoring_hub;

import com.backend.home_monitoring_hub.mqtt.MqttSubscriber;
import com.backend.home_monitoring_hub.reading.ReadingRepository;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MqttIngestionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    static GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf");

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("mqtt.broker.url", MqttIngestionIntegrationTest::brokerUrl);
    }

    @Autowired
    private ReadingRepository repository;

    @Autowired
    private MqttSubscriber subscriber;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        await().atMost(Duration.ofSeconds(10)).until(subscriber::isSubscribed);
    }

    @Test
    void savesPublishedReadingAndServesItThroughTheApi() throws Exception {
        publish("""
                {"timestamp":"2026-10-25T18:40:39-03:00","temperature":24.5,"humidity":null,
                 "light":237.5,"pressure":1010.67,"altitude":21.5}
                """);

        await().atMost(Duration.ofSeconds(10)).until(() -> repository.count() == 1);

        mockMvc.perform(get("/api/readings/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").value("2026-10-25T21:40:39Z"))
                .andExpect(jsonPath("$.temperature").value(24.5))
                .andExpect(jsonPath("$.humidity").value(nullValue()));
    }

    @Test
    void skipsInvalidMessagesAndKeepsListening() throws Exception {
        publish("not json");
        publish("""
                {"timestamp":"2026-10-25T18:40:44-03:00","temperature":24.6}
                """);

        await().atMost(Duration.ofSeconds(10)).until(() -> repository.count() == 1);
    }

    private static String brokerUrl() {
        return "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
    }

    private static void publish(String payload) throws MqttException {
        MqttClient publisher = new MqttClient(brokerUrl(), "test-publisher", new MemoryPersistence());
        try {
            publisher.connect();
            publisher.publish("home/sensors", payload.getBytes(StandardCharsets.UTF_8), 1, false);
            publisher.disconnect();
        } finally {
            publisher.close();
        }
    }
}