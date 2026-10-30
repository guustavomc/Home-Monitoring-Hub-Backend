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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class MqttSubscriber implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);

    private static final long FIRST_RETRY_DELAY_MS = 1_000;
    private static final long MAX_RETRY_DELAY_MS = 30_000;

    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final ReadingRepository repository;
    private final ObjectMapper objectMapper;
    private final String brokerUrl;
    private final String topic;
    private final String clientId;

    // Retries the first connect; Paho's automatic reconnect only covers connections lost after that
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-connector");
        thread.setDaemon(true);
        return thread;
    });

    private MqttClient client;
    private volatile boolean subscribed = false;
    private volatile boolean stopping = false;

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
    public void start() throws MqttException {
        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        client.setCallback(this);
        // Connect in the background so the app starts even when the broker is down
        connector.execute(() -> connect(FIRST_RETRY_DELAY_MS));
    }

    private void connect(long retryDelayMs) {
        if (stopping) {
            return;
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        try {
            log.info("Connecting to MQTT broker {}", brokerUrl);
            client.connect(options);
        } catch (MqttException e) {
            log.warn("Could not connect to MQTT broker {} ({}), retrying in {} s",
                    brokerUrl, e.getMessage(), retryDelayMs / 1000);
            long nextDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_DELAY_MS);
            connector.schedule(() -> connect(nextDelayMs), retryDelayMs, TimeUnit.MILLISECONDS);
        }
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
        String deviceId = topic.substring(topic.lastIndexOf('/') + 1);
        if (!DEVICE_ID.matcher(deviceId).matches()) {
            log.warn("Ignoring message on topic {}: invalid device id", topic);
            return;
        }
        try {
            ReadingMessage reading = objectMapper.readValue(payload, ReadingMessage.class);
            if (reading.timestamp() == null) {
                log.warn("Ignoring message without timestamp: {}", payload);
                return;
            }
            repository.save(reading.toEntity(deviceId));
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

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getTopic() {
        return topic;
    }

    @PreDestroy
    public void disconnect() throws MqttException, InterruptedException {
        stopping = true;
        connector.shutdownNow();
        // Let a connect attempt that's already running finish before closing the client
        connector.awaitTermination(15, TimeUnit.SECONDS);

        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        if (client != null) {
            client.close();
        }
    }
}