<p align="center">
  <img src="./assets/readme/hero.svg" width="100%" alt="SeatLock: Thousands of buyers. One seat. Zero double-bookings.">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java_21-E8B04B?style=for-the-badge" alt="Java 21"> <img src="https://img.shields.io/badge/Spring_Boot_4.1-E8B04B?style=for-the-badge" alt="Spring Boot 4.1"> <img src="https://img.shields.io/badge/PostgreSQL_16-E8B04B?style=for-the-badge" alt="PostgreSQL 16"> <img src="https://img.shields.io/badge/Redis_7-E8B04B?style=for-the-badge" alt="Redis 7"> <img src="https://img.shields.io/badge/Kafka_4.1-E8B04B?style=for-the-badge" alt="Kafka 4.1"> <img src="https://img.shields.io/badge/k6-E8B04B?style=for-the-badge" alt="k6">
</p>

<img src="./assets/readme/title-features.svg" width="100%" alt="01 Features: What it does.">
<img src="./assets/readme/features.svg" width="100%" alt="SeatLock features">

<img src="./assets/readme/title-flow.svg" width="100%" alt="02 Booking Flow: From browse to booked.">
<img src="./assets/readme/flow.svg" width="100%" alt="Browse → Queue → Hold → Pay → Confirm → Release → Notify">

<img src="./assets/readme/title-table.svg" width="100%" alt="03 Load Test: 500 buyers, 100 seats.">
<img src="./assets/readme/table.svg" width="100%" alt="Load Test">

<img src="./assets/readme/title-stack.svg" width="100%" alt="04 Tech stack: The tools behind it.">
<img src="./assets/readme/stack.svg" width="100%" alt="SeatLock tech stack">

<img src="./assets/readme/title-run.svg" width="100%" alt="05 Run it: Try it yourself.">

## 🌐 Live demo

Open `/` on a running instance for an interactive demo: a **live** seat map (seats turn amber/red on every open screen within a second, pushed by the server), hold / pay / cancel, **email confirmations**, the live double-booking audit, and a **"50 buyers, 1 seat" race** button.

- **Deploy your own free public demo** (Render + Neon + Upstash, no credit card): see [`DEPLOY.md`](./DEPLOY.md).
- **Run it locally:** start the app (see *Getting Started*) and open http://localhost:8080/.

**Live:** https://seatlock-7ba6.onrender.com (free server: it sleeps when idle, so the first visit can take about 2 minutes to wake it up)


## 🚀 Getting Started

### Prerequisites
- Java 21
- Docker & Docker Compose

### Run locally

```bash
git clone https://github.com/HemashreeVI/seatlock-.git
cd seatlock-
```
```bash
docker compose up -d          # PostgreSQL, Redis, Kafka (ports bound to 127.0.0.1)
```
```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Or run **everything** in Docker, with no Java installed:

```bash
docker compose --profile app up -d --build
```

Prometheus + Grafana (dashboard at http://localhost:3000, anonymous view-only, log in as `admin` / `admin` to edit; Prometheus at http://localhost:9090):

```bash
docker compose --profile observability up -d
```

### Without Kafka (demo mode)

The `demo` profile runs without a message broker: the outbox delivers messages to the handlers inside the app. It's what the free public deployment uses, and it needs only Postgres and Redis:

```bash
docker compose up -d postgres redis
```
```bash
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run      # Windows PowerShell: $env:SPRING_PROFILES_ACTIVE="demo"; .\mvnw.cmd spring-boot:run
```

### Verify

```bash
curl http://localhost:8080/actuator/health
```

### Run the tests

All tests start their own Postgres, Redis and Kafka containers (Docker must be running):

```bash
./mvnw verify
```


## 📖 API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/events` | Create an event; seats are generated from `sections` (`queueEnabled: true` for a waiting room) |
| `GET` | `/api/events` · `/api/events/{id}` | List / get events |
| `GET` | `/api/events/{id}/seats?status=AVAILABLE` | Seat map (cached, `X-Cache: HIT/MISS`) |
| `GET` | `/api/events/{id}/seats/stream` | Live seat statuses (Server-Sent Events) |
| `GET` | `/api/events/{id}/stats` | Seats and bookings by status, **double-booked seat count** |
| `POST` | `/api/events/{id}/queue` · `GET` same | Join the waiting room / poll position and admission token |
| `POST` | `/api/bookings` | Hold seats; optional `name` and `email` for notification emails (headers: `X-User-Id`, optional `Idempotency-Key`, `X-Queue-Token` for hot events) |
| `GET` | `/api/bookings/{id}` | Booking status |
| `DELETE` | `/api/bookings/{id}` | Cancel a hold, release the seats |
| `POST` | `/api/bookings/{id}/payments` | Pay (requires `Idempotency-Key`); `202 Accepted` |
| `GET` | `/api/payments/{id}` | Payment status |
| `GET` | `/api/notifications` | Your notifications |

Errors use the standard [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem-details JSON: `400` invalid input, `403` waiting-room admission required, `404` not found, `409` seat taken / wrong state, `422` idempotency key reused for a different request, `429` rate limited (with `Retry-After`).

### Example (bash)

The example uses [`jq`](https://jqlang.org/) to pick the ids out of each response, so it works even if your database already contains other events.

```bash
# create an event with 2 sections and remember its id
EVENT_ID=$(curl -s -X POST localhost:8080/api/events -H 'Content-Type: application/json' -d '{
  "name": "Coldplay Live", "venue": "DY Patil Stadium", "startsAt": "2027-01-20T19:00:00Z",
  "sections": [
    {"name": "VIP",     "rows": 2, "seatsPerRow": 5,  "priceCents": 500000},
    {"name": "GENERAL", "rows": 3, "seatsPerRow": 10, "priceCents": 150000}
  ]}' | jq -r .id)
```
```bash
# the ids of the first two seats of that event
SEATS=$(curl -s localhost:8080/api/events/$EVENT_ID/seats | jq -c '[.[0].id, .[1].id]')
```
```bash
# hold them for 10 minutes and remember the booking id
BOOKING_ID=$(curl -s -X POST localhost:8080/api/bookings -H 'Content-Type: application/json' \
  -H 'X-User-Id: alice' -H 'Idempotency-Key: hold-1' \
  -d "{\"eventId\": $EVENT_ID, \"seatIds\": $SEATS}" | jq -r .id)
```
```bash
# pay ("tok_fail..." simulates a declined card)
curl -s -X POST localhost:8080/api/bookings/$BOOKING_ID/payments -H 'Content-Type: application/json' \
  -H 'X-User-Id: alice' -H 'Idempotency-Key: pay-1' -d '{"paymentToken": "tok_visa"}'
```
```bash
# a moment later: CONFIRMED, seats BOOKED
curl -s localhost:8080/api/bookings/$BOOKING_ID -H 'X-User-Id: alice'
```


## 📁 Project Structure

```
src/main/java/com/HemashreeVI/seatlock/
├── event/          events, seat generation, stats
├── seat/           seat entity, seat map + Redis cache
├── booking/        holds, cancel, expiry sweeper, saga listener
│   └── locking/    optimistic / pessimistic / Redis strategies
├── payment/        idempotent payment requests, processor, fake gateway
├── notification/   notification consumer (inbox de-duplication)
├── messaging/      outbox, relay, topics, message payloads
├── queue/          virtual waiting room
├── ratelimit/      token bucket filter
├── observability/  business metrics
├── config/         typed configuration, Kafka, scheduling
└── common/         errors -> problem-details JSON
src/main/resources/db/migration/   Flyway V1..V4
load-test/                         k6 script
observability/                     Prometheus config, Grafana dashboard
```


## 🗺️ Roadmap

- [x] Project setup with Docker Compose (PostgreSQL, Redis)
- [x] **Stage 1:** Core domain: events, seats, bookings, REST APIs
- [x] **Stage 2:** Concurrency control, seat holds, idempotency
- [x] **Stage 3:** Kafka-based payment & notification flow, outbox pattern, saga
- [x] **Stage 4:** Caching, rate limiting, virtual waiting queue
- [x] **Stage 5:** Observability, load testing, design document
- [ ] Next: Spring Security (JWT), refund flow for late payment results, multi-instance load test


## 📄 Design Decisions

Locking strategies, consistency choices, failure handling and known limitations are explained in [`DESIGN.md`](./DESIGN.md).

<p align="center">
  <img src="./assets/readme/footer.svg" width="100%" alt="No seat is ever held by two bookings at the same time. Built by Hemashree.">
</p>
