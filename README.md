# Home-Monitoring-Hub-Backend

![CI](https://github.com/guustavomc/Home-Monitoring-Hub-Backend/actions/workflows/ci.yml/badge.svg)

Spring Boot service that subscribes to the MQTT sensor data published by the [Home-Monitoring-Hub](https://github.com/guustavomc/Home-Monitoring-Hub) firmware, saves the readings to PostgreSQL, and serves them through a REST API.

> **Status:** early development. MQTT ingestion, PostgreSQL persistence and the REST API work; it currently runs on a development PC. See the [Roadmap](#roadmap) for what's next.

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
    ├──▶ PostgreSQL (persist readings) ──▶ Grafana (dashboard)
    │
    └──▶ REST API (GET /api/readings)
```

---

## Tech Stack

| Layer       | Technology                                |
|-------------|-------------------------------------------|
| Language    | Java 17+                                  |
| Framework   | Spring Boot 4.x                           |
| MQTT Client | Eclipse Paho                              |
| Database    | PostgreSQL                                |
| ORM         | Spring Data JPA                           |
| Migrations  | Flyway                                    |
| Dashboards  | Grafana                                   |
| Testing     | JUnit 6, Mockito, MockMvc, Testcontainers |
| CI          | GitHub Actions                            |
| Build tool  | Maven                                     |

---

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker with Docker Compose (runs Mosquitto, PostgreSQL and Grafana)
- [Home-Monitoring-Hub](https://github.com/guustavomc/Home-Monitoring-Hub) firmware flashed and publishing data

---

## Getting Started

1. **Clone the repository**
  ```bash
  git clone https://github.com/guustavomc/Home-Monitoring-Hub-Backend.git
  cd Home-Monitoring-Hub-Backend
  ```

2. **Start Mosquitto, PostgreSQL and Grafana** (from the repo root)
  ```bash
  cp .env.example .env   # then set DB_PASSWORD and GRAFANA_ADMIN_PASSWORD
  docker compose up -d
  ```

3. **Configure the application** (see [Configuration](#configuration))

4. **Run the application** (from the `home-monitoring-hub/` folder)
  ```bash
  cd home-monitoring-hub
  mvn spring-boot:run
  ```

### Quick start on Windows

`run-local.ps1` does steps 2–4 in one command. It starts Mosquitto, PostgreSQL and Grafana, waits until the database is ready, loads `DB_USERNAME` / `DB_PASSWORD` from `.env` and runs the app. Run it from the repo root, after creating `.env`:

```powershell
.\run-local.ps1
```

If Mosquitto and PostgreSQL run on the Raspberry Pi instead of this PC, skip Docker and point the app at the Pi:

```powershell
.\run-local.ps1 -ServiceHost 192.168.1.136 -SkipDocker
```

In that case, `.env` on this PC must have the same `DB_PASSWORD` as the Pi.

### Grafana

Open **http://localhost:3000** and log in as `admin` with the `GRAFANA_ADMIN_PASSWORD` from `.env`. The **Home Monitoring** dashboard (under Dashboards → Home Monitoring) shows:

- temperature, humidity, light and pressure over time, averaged per minute. Minutes with no data show as gaps, e.g. when the ESP32 stopped publishing.
- **Readings in the last 5 min**: about 60 when the ESP32 publishes every 5 s; turns red below 50.

Grafana reads directly from PostgreSQL. The data source (`grafana/provisioning/datasources/`) and the dashboard (`grafana/dashboards/home-monitoring.json`) are provisioned from files, so they are recreated the same way on any machine.

To change the dashboard: edit it in the Grafana UI and save, then **Export → Export as JSON** with "Export the dashboard to use in another instance" **off**, overwrite `grafana/dashboards/home-monitoring.json` and commit it. On restart, the file wins over UI changes.

> Grafana only reads `GRAFANA_ADMIN_PASSWORD` the first time it starts. To change it later, use the Grafana UI (profile → Change password).

---

## Running the tests

With Docker running, from `home-monitoring-hub/`:

```bash
mvn verify
```

| Test                           | Type                           | Covers                                                                          |
|--------------------------------|--------------------------------|---------------------------------------------------------------------------------|
| `ReadingMessageTest`           | Unit                           | JSON parsing: offset timestamps converted to UTC, `null` values, unknown fields |
| `ReadingServiceTest`           | Unit (Mockito)                 | `limit` capping, 400 errors, which query runs                                   |
| `ReadingControllerTest`        | Web slice (MockMvc)            | Status codes, JSON shape, 404 on `/latest`, invalid parameters                  |
| `MqttIngestionIntegrationTest` | Integration (Testcontainers)   | Publishes over MQTT, checks the reading is saved and returned by the API        |

The unit and web tests don't need Docker. The integration test starts its own PostgreSQL and Mosquitto containers, so it doesn't touch the Docker Compose data. The first run is slower while the images download. A single `MQTT connection lost` warning at the end is expected: the Mosquitto container stops before the app shuts down.

[GitHub Actions](.github/workflows/ci.yml) runs the same `mvn verify` on every pull request and on every push to `main`.

---

## Configuration

`src/main/resources/application.properties` reads its settings from environment variables, with local defaults:

```properties
# PostgreSQL
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/home_monitoring}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:}
spring.jpa.hibernate.ddl-auto=validate

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

### Database schema

The schema is created by [Flyway](https://flywaydb.org/) from the SQL files in `src/main/resources/db/migration/`. On startup Flyway runs any migration the database hasn't applied yet (tracked in the `flyway_schema_history` table), and Hibernate only checks that the entities match (`ddl-auto=validate`), so the app won't start if they don't.

To change the schema, add a new file named `V<next number>__<description>.sql`, e.g. `V2__create_device_status.sql`. Never edit a migration that has already run: Flyway stores a checksum of each one and refuses to start if it changes.

> If your database was created before Flyway was added (by `ddl-auto=update`), Flyway refuses to start with "Found non-empty schema(s) without schema history table". Drop the old table so Flyway can recreate it (this deletes the saved readings):
> ```bash
> docker compose exec postgres psql -U postgres -d home_monitoring -c "DROP TABLE readings;"
> ```

Docker Compose reads its own settings from `.env` in the repo root (see `.env.example`):

| Variable                 | Used by               | Notes                                                       |
|--------------------------|-----------------------|-------------------------------------------------------------|
| `DB_USERNAME`            | PostgreSQL, Grafana   | Defaults to `postgres`                                      |
| `DB_PASSWORD`            | PostgreSQL, Grafana   | Required; only applied when the database is first created   |
| `GRAFANA_ADMIN_PASSWORD` | Grafana               | Required; password for the `admin` user, set on first start |

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

| Method | Endpoint                        | Description                                                  |
|--------|---------------------------------|--------------------------------------------------------------|
| GET    | `/api/readings`                 | Most recent readings, newest first (default 100, max 1000)   |
| GET    | `/api/readings?limit=N`         | Last N readings (capped at 1000)                             |
| GET    | `/api/readings?from=...&to=...` | Readings in a time range, newest first (`from`/`to` optional) |
| GET    | `/api/readings/latest`          | Most recent reading, or 404 if there are none yet            |

`from` and `to` are ISO 8601 instants, e.g. `2026-10-25T20:00:00Z`. Use the `Z` (UTC) form in URLs, or encode `+` as `%2B`.

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

### Done
- [x] Docker Compose for Mosquitto and PostgreSQL
- [x] MQTT ingestion: readings saved to PostgreSQL
- [x] REST API: `/api/readings` (with `limit` / `from` / `to`) and `/api/readings/latest`
- [x] Grafana: provisioned PostgreSQL data source and Home Monitoring dashboard
- [x] Tests and CI: unit, web and Testcontainers integration tests, run by GitHub Actions on every PR
- [x] Flyway migrations instead of `ddl-auto=update`

### Next
1. **Device status**: subscribe to the firmware's `home/hub/status` (`online` / `offline`, MQTT last will), store status changes, and show them in Grafana.
2. **Run the backend in Docker**: a Dockerfile and a backend service in Docker Compose, so `docker compose up -d` starts the whole stack. Healthchecks and `depends_on` also remove the "broker must be up at startup" limitation.
3. **Move to the Raspberry Pi**: run the stack 24/7 on the Pi instead of the development PC, so ingestion no longer depends on the PC being on or on its IP. Point the firmware's `MQTT_BROKER` at the Pi.

### Later
- **Read-only database user for Grafana** instead of the `postgres` superuser
- **MQTT authentication**: username and password in Mosquitto, the firmware and the backend
- **Alerts**: notifications when readings cross thresholds
- **Retention**: aggregate old readings (per-minute or per-hour averages) to keep queries fast
- **Edge AI**: anomaly detection model (TensorFlow Lite) running on the Raspberry Pi
- **Documentation**: architecture decision records, data flow diagrams
