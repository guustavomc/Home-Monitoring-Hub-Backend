package com.backend.home_monitoring_hub.mqtt;

import com.backend.home_monitoring_hub.reading.ReadingMessage;
import com.backend.home_monitoring_hub.reading.ReadingRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
public class MqttSubscriber implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);

    private final ReadingRepository repository;
    private final ObjectMapper objectMapper;
    private final String brokerUrl;
    private final String topic;
    private final String clientId;

    private MqttClient client;
    private volatile boolean subscribed = false;

    public MqttSubscriber(ReadingRepository repository,
                          ObjectMapper objectMapper,
                          @Value("${mqtt.broker.url}") String brokerUrl,
                          @Value("${mqtt.topic}") String topic,
                          @Value("${mqtt.client-id}") String clientId) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.brokerUrl = brokerUrl;
        this.topic = topic;
        this.clientId = clientId;
    }

    @PostConstruct
    public void connect() throws MqttException {
        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        client.setCallback(this);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        log.info("Connecting to MQTT broker {}", brokerUrl);
        client.connect(options);
    }

    // Called on the first connect and after every automatic reconnect
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(topic, 1);
            subscribed = true;
            log.info("{} to {}, subscribed to {}", reconnect ? "Reconnected" : "Connected", serverURI, topic);
        } catch (MqttException e) {
            log.error("Failed to subscribe to {}", topic, e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            ReadingMessage reading = objectMapper.readValue(payload, ReadingMessage.class);
            if (reading.timestamp() == null) {
                log.warn("Ignoring message without timestamp: {}", payload);
                return;
            }
            repository.save(reading.toEntity());
            log.debug("Saved reading: {}", payload);
        } catch (Exception e) {
            // Never throw from here: Paho disconnects the client if this method throws
            log.warn("Could not process MQTT message: {}", payload, e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        subscribed = false;
        log.warn("MQTT connection lost, reconnecting automatically", cause);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Only used when publishing; this service only subscribes
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    @PreDestroy
    public void disconnect() throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        if (client != null) {
            client.close();
        }
    }
}