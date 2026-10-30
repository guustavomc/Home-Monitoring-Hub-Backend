package com.backend.home_monitoring_hub;

import com.backend.home_monitoring_hub.mqtt.MqttSubscriber;
import com.backend.home_monitoring_hub.reading.ReadingRepository;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MqttStartupWithoutBrokerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    // Fixed host port, so the broker can start after the app on the URL the app already uses
    private static final int BROKER_PORT = freePort();

    // Not a @Container: started by the test, after the app is already running
    private static final GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(BROKER_PORT), ExposedPort.tcp(1883))));

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("mqtt.broker.url", () -> "tcp://localhost:" + BROKER_PORT);
    }

    @Autowired
    private MqttSubscriber subscriber;

    @Autowired
    private ReadingRepository repository;

    @Autowired
    private MockMvc mockMvc;

    @AfterAll
    static void stopBroker() {
        mosquitto.stop();
    }

    @Test
    void startsWithoutBrokerAndConnectsOnceItIsUp() throws Exception {
        // The app started; only MQTT is down
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.mqtt.status").value("DOWN"));

        mosquitto.start();

        // Retries back off to at most 30 s, so allow a bit more than that
        await().atMost(Duration.ofSeconds(45)).until(subscriber::isSubscribed);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.mqtt.status").value("UP"));

        publish("""
                {"timestamp":"2026-10-25T18:40:39-03:00","temperature":24.5}
                """);
        await().atMost(Duration.ofSeconds(10)).until(() -> repository.count() == 1);
    }

    private static void publish(String payload) throws Exception {
        MqttClient publisher = new MqttClient("tcp://localhost:" + BROKER_PORT, "test-publisher", new MemoryPersistence());
        try {
            publisher.connect();
            publisher.publish("home/sensors", payload.getBytes(StandardCharsets.UTF_8), 1, false);
            publisher.disconnect();
        } finally {
            publisher.close();
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free port for the test broker", e);
        }
    }
}