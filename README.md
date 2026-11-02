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

The web app is a complete booking site: **log in / sign up** → **browse events** (search and categories) → **pick seats on a live seat map** → **checkout** with a 10-minute hold timer (test-mode payment) → **e-tickets with QR codes** under *My bookings* → organizer **check-in scanner**. Pages: `/login.html`, `/` (events), `/event.html`, `/checkout.html`, `/bookings.html`, `/checkin.html`, and the engineering showcase at `/demo.html`.

The engineering showcase (`/demo.html`) has: **sign up / log in**, a **live** seat map (seats turn amber/red on every open screen within a second, pushed by the server), hold / pay / cancel, **e-tickets with QR codes**, an organizer **check-in scanner** at `/checkin.html`, **email confirmations**, the live double-booking audit, and a **"50 buyers, 1 seat" race** button.

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
| `POST` | `/api/auth/register` · `/api/auth/login` | Sign up / log in → a JWT (send it as `Authorization: Bearer <token>`) |
| `POST` | `/api/auth/guest` | Throwaway guest token (no account) |
| `GET` | `/api/auth/me` | Who the token belongs to |
| `POST` | `/api/events` | **Organizer only.** Create an event; seats are generated from `sections` (`queueEnabled: true` for a waiting room) |
| `GET` | `/api/events` · `/api/events/{id}` | List / get events |
| `GET` | `/api/events/{id}/seats?status=AVAILABLE` | Seat map (cached, `X-Cache: HIT/MISS`) |
| `GET` | `/api/events/{id}/seats/stream` | Live seat statuses (Server-Sent Events) |
| `GET` | `/api/events/{id}/stats` | Seats and bookings by status, **double-booked seat count** |
| `POST` | `/api/events/{id}/queue` · `GET` same | Join the waiting room / poll position and admission token |
| `POST` | `/api/bookings` | Hold seats for the logged-in user (optional `Idempotency-Key`; `X-Queue-Token` for hot events). Notifications go to the account's email |
| `GET` | `/api/bookings` | Your recent bookings |
| `GET` | `/api/bookings/{id}` | Booking status |
| `DELETE` | `/api/bookings/{id}` | Cancel a hold, release the seats |
| `POST` | `/api/bookings/{id}/payments` | Pay (requires `Idempotency-Key`); `202 Accepted` |
| `GET` | `/api/payments/{id}` | Payment status |
| `GET` | `/api/notifications` | Your notifications |
| `GET` | `/api/tickets` | Your e-tickets (one per seat of each paid booking) |
| `GET` | `/api/tickets/{id}/qr.png` | The ticket's QR code |
| `POST` | `/api/tickets/check-in` · `/api/tickets/check-in/scan` | **Organizer only.** Check a ticket in by code, or from a photo of its QR code → `ADMITTED` / `ALREADY_USED` / `INVALID` |

Everything except browsing events and seats needs a login. Organizer endpoints need the account set by `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

Errors use the standard [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem-details JSON: `400` invalid input, `401` not logged in, `403` organizer only / waiting-room admission required, `404` not found, `409` seat taken / wrong state, `422` idempotency key reused for a different request, `429` rate limited (with `Retry-After`).

### Example (bash)

The example uses [`jq`](https://jqlang.org/) to pick values out of each response. Start the app with an organizer account, e.g. `ADMIN_EMAIL=me@example.com ADMIN_PASSWORD=change-me-123`.

```bash
# log in as the organizer and create an event
ADMIN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email": "me@example.com", "password": "change-me-123"}' | jq -r .token)
EVENT_ID=$(curl -s -X POST localhost:8080/api/events -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{
  "name": "Coldplay Live", "venue": "DY Patil Stadium", "startsAt": "2027-01-20T19:00:00Z",
  "sections": [{"name": "VIP", "rows": 2, "seatsPerRow": 5, "priceCents": 500000}]}' | jq -r .id)
```
```bash
# a fan signs up, holds two seats and pays
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name": "Alice", "email": "alice@example.com", "password": "alice-pass-1"}' | jq -r .token)
SEATS=$(curl -s localhost:8080/api/events/$EVENT_ID/seats | jq -c '[.[0].id, .[1].id]')
BOOKING_ID=$(curl -s -X POST localhost:8080/api/bookings -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: hold-1' -d "{\"eventId\": $EVENT_ID, \"seatIds\": $SEATS}" | jq -r .id)
curl -s -X POST localhost:8080/api/bookings/$BOOKING_ID/payments -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: pay-1' -d '{"paymentToken": "tok_visa"}'
```
```bash
# a moment later: two tickets; the organizer checks the first one in (a second scan says ALREADY_USED)
CODE=$(curl -s localhost:8080/api/tickets -H "Authorization: Bearer $TOKEN" | jq -r '.[0].code')
curl -s -X POST localhost:8080/api/tickets/check-in -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d "{\"code\": \"$CODE\"}" | jq .result
```


## 📁 Project Structure

```
src/main/java/com/HemashreeVI/seatlock/
├── event/          events, seat generation, stats
├── seat/           seat entity, seat map + Redis cache
├── auth/           sign-up / log-in, JWT, security rules, organizer account
├── ticket/         e-tickets, QR codes, check-in
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
src/main/resources/static/          web app: login, events, event (seat map), checkout, bookings, check-in; demo.html; assets/ (shared CSS/JS)
src/main/resources/db/migration/   Flyway V1..V7
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
- [x] Live seat map (Server-Sent Events), email notifications
- [x] Accounts and login (Spring Security + JWT), e-tickets with QR codes, check-in scanner
- [ ] Next: refund flow for late payment results, multi-instance load test


## 📄 Design Decisions

Locking strategies, consistency choices, failure handling and known limitations are explained in [`DESIGN.md`](./DESIGN.md).

<p align="center">
  <img src="./assets/readme/footer.svg" width="100%" alt="No seat is ever held by two bookings at the same time. Built by Hemashree.">
</p>
