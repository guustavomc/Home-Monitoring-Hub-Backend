package com.backend.home_monitoring_hub;

import com.backend.home_monitoring_hub.mqtt.MqttSubscriber;
import com.backend.home_monitoring_hub.reading.Reading;
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

import static org.assertj.core.api.Assertions.assertThat;
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
        publish("home/sensors/livingroom", """
                {"timestamp":"2026-10-25T18:40:39-03:00","temperature":24.5,"humidity":null,
                 "light":237.5,"pressure":1010.67,"altitude":21.5}
                """);

        await().atMost(Duration.ofSeconds(10)).until(() -> repository.count() == 1);

        mockMvc.perform(get("/api/readings/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("livingroom"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-25T21:40:39Z"))
                .andExpect(jsonPath("$.temperature").value(24.5))
                .andExpect(jsonPath("$.humidity").value(nullValue()));
    }

    @Test
    void keepsReadingsFromEachDeviceApart() throws Exception {
        publish("home/sensors/livingroom", """
                {"timestamp":"2026-10-25T18:40:39-03:00","temperature":24.5}
                """);
        publish("home/sensors/garage", """
                {"timestamp":"2026-10-25T18:40:40-03:00","temperature":12.0}
                """);

        await().atMost(Duration.ofSeconds(10)).until(() -> repository.count() == 2);

        mockMvc.perform(get("/api/readings/latest").param("device", "livingroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(24.5));

        mockMvc.perform(get("/api/readings").param("device", "garage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deviceId").value("garage"));

        mockMvc.perform(get("/api/readings/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("garage"))
                .andExpect(jsonPath("$[1]").value("livingroom"));
    }

    @Test
    void skipsInvalidMessagesAndKeepsListening() throws Exception {
        publish("home/sensors/livingroom", "not json");
        // Matches the subscription but isn't a valid device id
        publish("home/sensors/bad.id", """
                {"timestamp":"2026-10-25T18:40:43-03:00","temperature":24.6}
                """);
        publish("home/sensors/livingroom", """
                {"timestamp":"2026-10-25T18:40:44-03:00","temperature":24.6}
                """);

        await().atMost(Duration.ofSeconds(10)).until(() -> repository.count() == 1);
        assertThat(repository.findAll()).extracting(Reading::getDeviceId).containsExactly("livingroom");
    }

    @Test
    void ignoresTheOldTopicWithoutDeviceId() throws Exception {
        publish("home/sensors", """
                {"timestamp":"2026-10-25T18:40:45-03:00","temperature":24.7}
                """);
        publish("home/sensors/livingroom", """
                {"timestamp":"2026-10-25T18:40:46-03:00","temperature":24.8}
                """);

        await().atMost(Duration.ofSeconds(10)).until(() -> repository.count() == 1);
        assertThat(repository.findAll()).extracting(Reading::getTemperature).containsExactly(24.8);
    }

    @Test
    void reportsHealthOfDatabaseAndMqtt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.mqtt.status").value("UP"));
    }

    private static String brokerUrl() {
        return "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
    }

    private static void publish(String topic, String payload) throws MqttException {
        MqttClient publisher = new MqttClient(brokerUrl(), "test-publisher", new MemoryPersistence());
        try {
            publisher.connect();
            publisher.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
            publisher.disconnect();
        } finally {
            publisher.close();
        }
    }
}