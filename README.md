# Home-Monitoring-Hub-Backend

Spring Boot service that subscribes to the MQTT sensor data published by the [Home-Monitoring-Hub](https://github.com/guustavomc/Home-Monitoring-Hub) firmware, saves the readings to PostgreSQL, and serves them through a REST API.

> **Status:** early development. The project skeleton exists; the MQTT subscriber, persistence and REST API below are being implemented.

---

## Architecture

```
ESP32 (sensors)
    │
    │  MQTT publish (home/sensors)
    ▼
Mosquitto Broker
    │
    │  MQTT subscribe
    ▼
Spring Boot Service
    │
    ├──▶ PostgreSQL (persist readings)
    │
    └──▶ REST API (GET /api/readings)
```

---

## Tech Stack

| Layer        | Technology              |
|--------------|-------------------------|
| Language     | Java 17+                |
| Framework    | Spring Boot 4.x         |
| MQTT Client  | Eclipse Paho            |
| Database     | PostgreSQL              |
| ORM          | Spring Data JPA         |
| Build tool   | Maven                   |

---

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker with Docker Compose (runs Mosquitto and PostgreSQL)
- [Home-Monitoring-Hub](https://github.com/guustavomc/Home-Monitoring-Hub) firmware flashed and publishing data

---

## Getting Started

1. **Clone the repository**
  ```bash
  git clone https://github.com/guustavomc/Home-Monitoring-Hub-Backend.git
  cd Home-Monitoring-Hub-Backend
  ```

2. **Start Mosquitto and PostgreSQL** (from the repo root)
  ```bash
  cp .env.example .env   # then set DB_PASSWORD
  docker compose up -d
  ```

3. **Configure the application** (see [Configuration](#configuration))

4. **Run the application** (from the `home-monitoring-hub/` folder)
  ```bash
  cd home-monitoring-hub
  mvn spring-boot:run
  ```

### Quick start on Windows

`run-local.ps1` does steps 2–4 in one command. It starts Mosquitto and PostgreSQL, waits until the database is ready, loads `DB_USERNAME` / `DB_PASSWORD` from `.env` and runs the app. Run it from the repo root, after creating `.env`:

```powershell
.\run-local.ps1
```

If Mosquitto and PostgreSQL run on the Raspberry Pi instead of this PC, skip Docker and point the app at the Pi:

```powershell
.\run-local.ps1 -ServiceHost 192.168.1.136 -SkipDocker
```

In that case, `.env` on this PC must have the same `DB_PASSWORD` as the Pi.

---

## Configuration

`src/main/resources/application.properties` reads its settings from environment variables, with local defaults:

```properties
# PostgreSQL
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/home_monitoring}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:}
spring.jpa.hibernate.ddl-auto=update

# MQTT Broker
mqtt.broker.url=${MQTT_BROKER_URL:tcp://localhost:1883}
mqtt.topic=${MQTT_TOPIC:home/sensors}
mqtt.client-id=${MQTT_CLIENT_ID:home-monitoring-backend}
```

| Variable          | Example                                                 | Notes                                                              |
|-------------------|---------------------------------------------------------|--------------------------------------------------------------------|
| `DB_URL`          | `jdbc:postgresql://192.168.1.136:5432/home_monitoring` | IP of the machine running Docker Compose                           |
| `DB_USERNAME`     | `postgres`                                              | Same as in `.env`                                                  |
| `DB_PASSWORD`     | —                                                       | Same as in `.env`                                                  |
| `MQTT_BROKER_URL` | `tcp://192.168.1.136:1883`                              | Same broker the firmware uses (`MQTT_BROKER` in its `config.h`)    |
| `MQTT_TOPIC`      | `home/sensors`                                          | Default matches the firmware's `MQTT_TOPIC`                        |
| `MQTT_CLIENT_ID`  | `home-monitoring-backend`                               | Must differ from the ESP32's client ID and from any other instance |

The broker accepts anonymous connections, so no MQTT username or password is needed.

### Running on your PC against the Raspberry Pi

During development you can run the app on your PC while Mosquitto and PostgreSQL run on the Pi. Replace `192.168.1.136` with your Pi's IP and run from the `home-monitoring-hub/` folder.

**Windows (PowerShell):**

```powershell
$env:DB_URL = "jdbc:postgresql://192.168.1.136:5432/home_monitoring"
$env:DB_PASSWORD = "<password from the Pi's .env>"
$env:MQTT_BROKER_URL = "tcp://192.168.1.136:1883"
mvn spring-boot:run
```

**Linux / macOS (bash):**

```bash
export DB_URL=jdbc:postgresql://192.168.1.136:5432/home_monitoring
export DB_PASSWORD='<password from .env>'
export MQTT_BROKER_URL=tcp://192.168.1.136:1883
mvn spring-boot:run
```

These variables only last for the current terminal session. You can also set them in your IDE's run configuration.

When it's working, the log shows `Connected to tcp://192.168.1.136:1883, subscribed to home/sensors`, followed by a `Saved reading: {...}` line every 5 seconds (with DEBUG logging enabled). To check the saved rows on the Pi:

```bash
docker compose exec postgres psql -U postgres -d home_monitoring -c "SELECT count(*), max(timestamp) FROM readings;"
```

`max(timestamp)` is shown in UTC.

> ⚠️ Never put real credentials in `application.properties`. Pass them as environment variables instead.

---

## MQTT Message Format

The firmware publishes one JSON message per reading (every 5 s) to `home/sensors`:

```json
{
  "timestamp": "2026-10-20T20:00:00-03:00",
  "temperature": 24.5,
  "humidity": 61.2,
  "light": 340.0,
  "pressure": 1013.4,
  "altitude": 12.3
}
```

| Field         | Unit                       | Notes                                                    |
|---------------|----------------------------|----------------------------------------------------------|
| `timestamp`   | ISO 8601 with UTC offset   | Stored as `timestamptz` (an `Instant` in Java)           |
| `temperature` | °C                         | Average of DHT11 and BMP280, or whichever one is working |
| `humidity`    | %                          | DHT11                                                    |
| `light`       | lux                        | BH1750                                                   |
| `pressure`    | hPa                        | BMP280                                                   |
| `altitude`    | m                          | BMP280, estimated from pressure                          |

Any sensor value can be `null` when that sensor fails to read, so the numeric fields must be nullable (`Double`, not `double`).

---

## REST API

| Method | Endpoint                                 | Description                                         |
|--------|------------------------------------------|-----------------------------------------------------|
| GET    | `/api/readings`                          | Returns the most recent readings (default 100)      |
| GET    | `/api/readings?limit=N`                  | Returns the last N readings                         |
| GET    | `/api/readings?from=...&to=...`          | Returns readings within a time range (ISO 8601)     |
| GET    | `/api/readings/latest`                   | Returns the most recent reading                     |

**Example response:**
```json
{
  "id": 1,
  "timestamp": "2026-10-20T23:00:00Z",
  "temperature": 24.5,
  "humidity": 61.2,
  "light": 340.0,
  "pressure": 1013.4,
  "altitude": 12.3
}
```

Timestamps are returned in UTC.

---

## Related Repositories

- [Home-Monitoring-Hub](https://github.com/guustavomc/Home-Monitoring-Hub) — Firmware (sensors + display)
- Home-Monitoring-Hub-Dashboard *(coming soon)* — Frontend dashboard

---

## Roadmap

- **Layer 2 — Edge AI**: Anomaly detection model (TensorFlow Lite) running on Raspberry Pi
- **Layer 3 — Observability**: Grafana dashboard reading directly from PostgreSQL, Docker Compose (Mosquitto + PostgreSQL + backend + Grafana), GitHub Actions CI
- **Layer 4 — Documentation**: Architecture decision records, data flow diagrams
