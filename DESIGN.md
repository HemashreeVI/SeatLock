# SeatLock: Design Decisions

This document explains *why* SeatLock is built the way it is: the trade-offs behind each choice, how failures are handled, and what is deliberately left out.

---

## 1. Goals and non-goals

**Must hold, always**
1. A seat is never sold to two people (no double-booking).
2. A customer is never charged twice for one booking.
3. A seat that is held but never paid for returns to sale automatically.

**Should hold under load**
4. Traffic spikes degrade gracefully (queueing, 409s, 429s) instead of taking the database down.
5. Browsing doesn't compete with booking for database capacity.

**Non-goals (for now):** authentication (an `X-User-Id` header stands in for it), real payment providers, refunds, multi-region.

---

## 2. Architecture: modular monolith

One Spring Boot application, split into packages that act as modules: `event`, `seat`, `booking`, `payment`, `notification`, `queue`, `ratelimit`, `messaging`.

- **Why not microservices from day one?** The hardest part, the seat-hold transaction, must be atomic over seats, bookings and booking_seats. In one database that is a plain ACID transaction; across services it would need distributed coordination for no benefit.
- **Where the seams are:** booking ↔ payment ↔ notification communicate **only through Kafka messages** (the `messaging` package holds the contracts). The payment module could be moved into its own service without touching the booking code.

---

## 3. Data model and state machines

```
events 1──* seats                 bookings 1──* booking_seats *──1 seats
                                  bookings 1──* payments
outbox_events   processed_messages   notifications
```

**Seat:** `AVAILABLE → HELD → BOOKED`, or `HELD → AVAILABLE` (cancel / expiry / failed payment).

**Booking:** `HELD → PAYMENT_PENDING → CONFIRMED`; `HELD → CANCELLED | EXPIRED`; `PAYMENT_PENDING → CANCELLED` (payment failed).

**Money** is stored as integer cents (`BIGINT`), never floating point.

**Schema changes** only go through Flyway (`V1`–`V4`); Hibernate runs with `ddl-auto: validate`, so the app refuses to start if the code and the schema disagree.

---

## 4. Preventing double-booking: defence in depth

There are three layers. Any single one would prevent double-booking; together they survive bugs in the others.

### Layer 1: the locking strategy (configurable)
`seatlock.locking.strategy` selects how concurrent holds for the same seat are serialised:

| Strategy | Mechanism | Loser experience | Best when | Cost |
|---|---|---|---|---|
| **PESSIMISTIC** (default) | `SELECT … FOR UPDATE`, rows locked **in id order** | waits for the winner to commit, then sees `HELD` → 409 | high contention, exact semantics | a DB connection is held while waiting |
| **OPTIMISTIC** | read freely; `UPDATE … WHERE version = ?` | update matches 0 rows → `OptimisticLockException` → 409 | low contention | wasted work under heavy contention |
| **REDIS** | `SET seat-lock:{id} token NX PX 5000` per seat, fail fast | instant 409, no DB work | many instances, shield Postgres | extra moving part; not a correctness guarantee on its own (see below) |

*Deadlocks:* multi-seat requests always lock seats sorted by id, so two requests can never each hold a seat the other needs.

*Why the Redis lock isn't trusted alone:* a lock with a TTL can expire while its owner is paused (GC, network), letting a second owner in. The fix is a fencing token, and SeatLock already has one: the seat's `version` column (layer 2).

### Layer 2: optimistic version on every seat
Every strategy writes through Hibernate, which adds `AND version = ?` to each seat UPDATE. Even under PESSIMISTIC or REDIS, a conflicting write fails instead of silently overwriting.

### Layer 3: the database constraint
```sql
CREATE UNIQUE INDEX uq_booking_seats_one_active ON booking_seats (seat_id) WHERE active;
```
A seat can be in at most one *active* booking, enforced by PostgreSQL regardless of application code. A `CHECK` constraint also ties seat status to ownership (`AVAILABLE ⇔ booking_id IS NULL`).

### Verifying it
- `BookingConcurrencyTest` races 200 threads for one seat (and 200 overlapping multi-seat requests) under each strategy: exactly one winner, zero seats in two bookings.
- `/api/events/{id}/stats` returns `doubleBookedSeats`, a durable audit. A booking owns its seats from `created_at` until it is released (`updated_at` of a `CANCELLED`/`EXPIRED` booking; open-ended otherwise), and the audit counts seats where two bookings' ownership intervals overlap. It considers **every** booking ever made, including expired and cancelled ones, and doesn't rely on the `active` flag or the index it checks. A load test longer than the hold TTL therefore can't hide an overlap that has since expired. The k6 test fails if it's ever non-zero. (It compares timestamps written by the app, so instances with badly skewed clocks could produce a false positive, never a false negative of a real concurrent overlap within one instance.)

### Fast reject
After a sell-out, nearly every request is doomed. Before taking any lock, SeatLock checks the (Redis-cached) seat map and rejects seats already shown as taken. This **can only reject**: a seat shown as available still goes through the locked check. In the load test it raised pessimistic throughput from 897 to 2,171 req/s.

---

## 5. Seat holds and expiry

- A hold sets `bookings.expires_at = now + hold-ttl` (10 min) and marks each seat `HELD` with `hold_expires_at`.
- A sweeper runs every 5 s:
  `SELECT id FROM bookings WHERE status='HELD' AND expires_at < now() ORDER BY expires_at LIMIT 200 FOR UPDATE SKIP LOCKED`
  - **`SKIP LOCKED`** lets several app instances sweep in parallel without blocking each other or double-processing a booking.
  - A booking that is being paid right now holds its row lock, so the sweeper skips it.
- Only `HELD` bookings expire. Once payment starts (`PAYMENT_PENDING`), the booking is no longer swept, so we never release seats that are about to be paid for. A payment that never gets a result is handled by the payment reconciler instead (§7), so `PAYMENT_PENDING` can't last forever.

---

## 6. Idempotency

**Holds:** optional `Idempotency-Key`. A unique index on `(user_id, idempotency_key)` makes "same key → same booking" a database fact. A retry returns the original booking (`200` + `Idempotent-Replayed: true`). The same key with a different event or seat set gets `422`. If concurrent copies of one request race, the losers detect that the "competitor" was their own twin and return its booking instead of a 409.

**Payments:** `Idempotency-Key` is **required**.
- `payments.idempotency_key` is unique; a SHA-256 fingerprint of (user, booking, token) detects a key reused for a different request (`422`).
- `uq_payments_one_success` (one `SUCCEEDED` payment per booking) blocks a second charge even with a different key.
- The gateway is called with the same idempotency key, as real providers (Stripe and others) support, so even a redelivered Kafka message can't charge twice.

---

## 7. Payment saga over Kafka

```
HTTP  POST /payments ──► [tx] lock booking · payment PENDING · booking PAYMENT_PENDING · outbox(PaymentRequested)
relay outbox ──► seatlock.payments.requested
payment module ──► charge gateway (no DB tx held) ──► [tx] payment SUCCEEDED/FAILED · outbox(PaymentCompleted)
relay outbox ──► seatlock.payments.completed
booking module ──► [tx] CONFIRMED + seats BOOKED      | or compensation: CANCELLED + seats released
                    + outbox(BookingConfirmed)          |   + outbox(BookingCancelled)
notification module ──► one notification per booking event
```

### Why a transactional outbox?
"Write to the DB, then publish to Kafka" has two failure windows: the commit succeeds but the publish fails (lost message), or the publish succeeds but the commit rolls back (phantom message). SeatLock writes the message **into `outbox_events` in the same transaction** as the state change. A relay publishes unpublished rows in id order (`FOR UPDATE SKIP LOCKED`) and marks them published. If the relay crashes after sending but before marking, the message is sent again: **at-least-once**.

### So every consumer is idempotent
- **Payment processor:** skips payments that are no longer `PENDING`, re-checks under a row lock, and relies on the gateway's idempotency key.
- **Booking saga listener:** acts only if the booking is still `PAYMENT_PENDING`, under a row lock.
- **Notification consumer:** an *inbox* table `processed_messages(consumer, message_id)`; the insert and the side effect share a transaction, so a duplicate is detected and skipped. Covered by `OutboxRelayTest.redeliveredBookingEventIsNotNotifiedTwice`.

### Demo mode: the same outbox, no broker
Free hosting has no affordable Kafka, so `seatlock.messaging.mode=local` (set by the `demo` profile) swaps the transport. A `LocalOutboxDispatcher` reads unpublished outbox rows in id order and calls the same `MessageHandler` beans the Kafka listeners use. It marks a row published only **after** its handler succeeded, so delivery is still at-least-once and survives restarts. Failures are retried with a growing delay and parked after 4 attempts (the local dead-letter); the payment reconciler covers stuck payments exactly as in Kafka mode. Business code is unaware of the difference. What you lose is broker-level scaling across instances: the demo runs a single instance.

### Ordering
Messages are keyed by booking id, so all messages for a booking land on the same partition, in order.

### Poison messages
3 retries, 1 s apart, then the message is parked on `<topic>-dlt` so it can't block its partition. The recoverer writes to the **same partition number** on the dead-letter topic, so every `-dlt` topic is declared with the same partition count as its source. An auto-created one would get the broker default (often 1), and records from partitions 1 and 2 could then not be parked.

### Payments that never get a result: reconciliation
If the provider is down for longer than the Kafka retries (a few seconds), the `PaymentRequested` message ends up on the dead-letter topic and the payment stays `PENDING`. Nothing else would ever finish it, and the booking would stay `PAYMENT_PENDING` with its seats locked forever. The **payment reconciler** runs every 30 s and handles payments still `PENDING` after `pending-timeout` (1 min):
- It **re-drives** them: calls the gateway again with the payment's original idempotency key and token (stored on the payment), then records the result. Because the key is the same, a charge that already happened is returned by the provider, never repeated.
- If the provider is still unreachable after `give-up-after` (15 min), it fails the payment. The saga then releases the seats, and the error log asks for a refund check in case the provider had charged after all.

### Why charge outside the DB transaction
Holding row locks while waiting on a slow external provider would stall everything that touches that booking. The processor reads, calls the gateway with no transaction open, then writes the result in a short transaction.

---

## 8. Caching

- `GET /seats` is served from Redis (`seatlock:cache:seats:{eventId}`), with a database fallback if Redis is down.
- **Eviction happens after commit** (`@TransactionalEventListener(AFTER_COMMIT)`). Evicting before commit would let a concurrent reader re-cache the old state.
- A 5 s TTL is the safety net for a missed eviction.
- **The cache is for display only.** Holding a seat always takes the locked database path, so a stale cache can at worst show a seat that is already gone, and then the hold returns 409.

---

### Live seat map (Server-Sent Events)
- `GET /api/events/{id}/seats/stream` keeps an HTTP response open and pushes `seats` events.
- A committed seat change only marks its event *dirty* (`@TransactionalEventListener(AFTER_COMMIT)`). A flush every 300 ms sends each dirty event's statuses once. A rush of 1,000 holds therefore costs a few small queries, not 1,000 × viewers messages.
- **Why SSE rather than WebSockets:** the traffic is one-way (server → browser). SSE is plain HTTP, reconnects automatically in the browser, and passes through hosting proxies without extra setup. A heartbeat every 20 s keeps idle connections open.
- **Limits:** a cap on open streams (503 beyond it; the page falls back to periodic refresh), and subscribers live in one instance's memory. Several instances would need to fan changes out to each other, for example with Redis pub/sub.

### Email notifications
- The notification consumer stores the in-app notification and, inside the same transaction, asks for an email. The email is sent only **after commit**, so a rolled-back or redelivered message never emails for a notification that doesn't exist.
- The delivery result (`SENT`, `FAILED`, `LOGGED` when no provider is configured, `SKIPPED`) is recorded on the notification. A provider outage never undoes the booking or the in-app notification.
- **Anti-abuse on the public demo:** at most 10 emails per address per 24 h (plus Brevo's own 300/day).

## 9. Rate limiting

- A token bucket per user (`capacity` burst, `refill-per-second` sustained), applied to holds, payments and queue joins.
- Implemented as **one Lua script**: read, refill, take and write happen atomically in Redis, so two concurrent requests can't both take the last token. It uses Redis's own clock (`TIME`), so app servers with skewed clocks agree.
- **Two buckets per request:** per user (`X-User-Id`) and per client IP. `X-User-Id` isn't authenticated, so a client could invent a new one for every request; the IP bucket (larger, because users behind one NAT share an IP) caps that. Behind a reverse proxy, `server.forward-headers-strategy` must be set so the real client IP is used.
- **Path matching uses the same normalisation as Spring MVC** (URL-decoding, stripping `;matrix` parameters). Otherwise `POST /api/bookings;v=1`, which Spring still routes to the booking controller, would skip the limiter.
- Over the limit: `429` with `Retry-After`.
- **Fails open:** if Redis is down we let requests through; locking and constraints still protect correctness.

---

## 10. Virtual waiting room

For events created with `queueEnabled: true`:
- `POST /queue` adds the user to a FIFO sorted set (`ZADD NX`, so re-joining keeps your place).
- Every second, an admission job runs a **Lua script** that drops expired admissions, computes free room (`maxActiveUsers − active`), pops the next users, marks them admitted and stores their tokens, **atomically**. Several instances can run the job without admitting anyone twice or overfilling the room.
- The same script stops scheduling the event once nobody is waiting, in the same atomic step. `join()` always adds the user to the line before re-scheduling the event, so a user who joins at that exact moment can't end up in a line that is no longer being admitted from.
- The booking endpoint requires `X-Queue-Token` to match the user's token.
- **Effect:** at most `maxActiveUsers` people compete for seats at any moment, however many arrive.

---

## 11. Observability

- **Metrics (Micrometer → `/actuator/prometheus`):**
  - `seatlock_holds_total{result,strategy}`
  - `seatlock_hold_duration_seconds` (histogram → p50/p95/p99)
  - `seatlock_booking_transitions_total{status}`
  - `seatlock_outbox_backlog`
  - plus HTTP, JVM and Hikari metrics
- **Dashboard:** `observability/grafana/dashboards/seatlock.json`, auto-provisioned by `docker compose --profile observability up`.
- **Tracing:** Micrometer Tracing (Brave) puts `traceId-spanId` in every log line and propagates it over HTTP and Kafka headers. *Limitation:* the outbox relay starts a new trace, so one saga appears as several traces. Storing the `traceparent` in the outbox row would link them.

---

## 12. Testing strategy

- **Integration tests run against real Postgres, Redis and Kafka** via Testcontainers. Locking, `SKIP LOCKED`, partial indexes and Lua scripts can't be faithfully mocked.
- **Concurrency tests use a start gate** (`CountDownLatch`) so all threads fire at the same instant.
- **Saga tests run end to end** (HTTP → outbox → Kafka → consumers) and use Awaitility for asynchronous outcomes.
- **Load test (k6):** throughput and latency, plus the final double-booking check.

---

## 13. Known limitations and next steps

| Limitation | Why it's acceptable now | Next step |
|---|---|---|
| No authentication (`X-User-Id`); the per-IP rate limit is the only cap on rotating identities | Focus is concurrency and consistency | Spring Security + JWT, then rate-limit by authenticated user |
| A provider that charged but stayed unreachable past `give-up-after`, or a late success after cancellation, is only logged | Requires a 15-minute provider outage | Emit `RefundRequired` and automate refunds |
| Seat map cached as one JSON blob | Fine for venues up to tens of thousands of seats | Per-section keys or a Redis hash per event |
| Redis lock keys and queue Lua build keys dynamically | Single Redis node | Hash tags (`{eventId}`) for Redis Cluster |
| Live-update subscribers are held in one instance's memory | The demo runs one instance | Redis pub/sub fan-out between instances |
| Load test ran on one machine, from one IP, with rate limiting off | Shows correctness and relative cost of strategies | Multi-instance run behind a load balancer, distributed k6 |
| Kafka in docker-compose has no authentication | Dev setup; ports are bound to `127.0.0.1` | SASL/TLS and ACLs so only the payment module can publish payment results |
