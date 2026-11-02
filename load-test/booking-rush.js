// SeatLock load test: many users fighting over a limited number of seats.
//
//   k6 run load-test/booking-rush.js
//   k6 run -e BASE_URL=http://localhost:8080 -e SEATS=100 -e VUS=500 -e DURATION=30s load-test/booking-rush.js
//
// What it measures: the seat-HOLD path under contention (it does not pay for holds).
// At the end it asks the server for /api/events/{id}/stats and FAILS if any seat was ever owned
// by two bookings at the same time. That audit includes expired and cancelled bookings, so it
// stays valid for runs longer than the 10-minute hold TTL.
//
// Logins: the organizer account (ADMIN_EMAIL / ADMIN_PASSWORD, the same values the app was started
// with) creates the event; every VU is one buyer with its own guest login token.
//   k6 run -e ADMIN_EMAIL=me@example.com -e ADMIN_PASSWORD=change-me-123 load-test/booking-rush.js
//
// All traffic comes from one IP, so with the default rate limits most requests would get 429. To
// measure the booking path itself, start the app with rate limiting off:
//   -Dseatlock.rate-limit.enabled=false
// Throttled requests are still counted (holds_throttled_429) so you can see the limiter working.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEATS = parseInt(__ENV.SEATS || '100');
const MAX_SEATS_PER_ROW = 100;   // API limit
const MAX_ROWS = 26;             // API limit (rows A..Z)

const holdsWon = new Counter('holds_won');
const holdsLost = new Counter('holds_lost_409');
const throttled = new Counter('holds_throttled_429');
const doubleBooked = new Counter('double_booked_seats');

export const options = {
  scenarios: {
    rush: {
      executor: 'constant-vus',
      vus: parseInt(__ENV.VUS || '500'),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    double_booked_seats: ['count==0'],   // the whole point of SeatLock
    holds_won: ['count>0'],              // a run where nobody ever got a seat proves nothing
    http_req_failed: ['rate<0.01'],      // 409/429 are expected answers, not failures
    'http_req_duration{name:hold}': ['p(95)<500'],
  },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409, 429));

/** Exactly SEATS seats: full rows of 100 in one section, the remainder in a second section. */
export function sections(total) {
  if (!(total >= 1) || total > MAX_ROWS * MAX_SEATS_PER_ROW + MAX_SEATS_PER_ROW - 1) {
    throw new Error(`SEATS must be between 1 and ${MAX_ROWS * MAX_SEATS_PER_ROW + MAX_SEATS_PER_ROW - 1}`);
  }
  const result = [];
  const fullRows = Math.floor(total / MAX_SEATS_PER_ROW);
  const remainder = total % MAX_SEATS_PER_ROW;
  if (fullRows > 0) {
    result.push({ name: 'FLOOR', rows: fullRows, seatsPerRow: MAX_SEATS_PER_ROW, priceCents: 9900 });
  }
  if (remainder > 0) {
    result.push({ name: 'BALCONY', rows: 1, seatsPerRow: remainder, priceCents: 4900 });
  }
  return result;
}

export function setup() {
  const login = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: __ENV.ADMIN_EMAIL, password: __ENV.ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) {
    throw new Error(`Organizer login failed (${login.status}): pass -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=...`);
  }
  const startsAt = new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString();
  const res = http.post(`${BASE_URL}/api/events`, JSON.stringify({
    name: 'Load test sale', venue: 'k6 Arena', startsAt, sections: sections(SEATS),
  }), { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${login.json('token')}` } });
  if (res.status !== 201) {
    throw new Error(`Could not create the event: ${res.status} ${res.body}`);
  }
  const eventId = res.json('id');
  const seatIds = http.get(`${BASE_URL}/api/events/${eventId}/seats`).json().map((s) => s.id);
  if (seatIds.length !== SEATS) {
    throw new Error(`Expected ${SEATS} seats, got ${seatIds.length}`);
  }
  return { eventId, seatIds };
}

let token = null;   // one guest login per VU (module state is per VU in k6)

export default function (data) {
  if (!token) {
    const guest = http.post(`${BASE_URL}/api/auth/guest`, null, { tags: { name: 'login' } });
    if (guest.status !== 200) { throttled.add(guest.status === 429 ? 1 : 0); return; }
    token = guest.json('token');
  }
  // everyone wants the best seats: pick 1-2 seats biased towards the front rows
  const pick = () => data.seatIds[Math.floor(Math.pow(Math.random(), 2) * data.seatIds.length)];
  const wanted = [...new Set([pick(), pick()].slice(0, 1 + Math.floor(Math.random() * 2)))];

  const res = http.post(`${BASE_URL}/api/bookings`,
    JSON.stringify({ eventId: data.eventId, seatIds: wanted }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, tags: { name: 'hold' } });

  check(res, { 'hold answered 201/409/429': (r) => [201, 409, 429].includes(r.status) });
  holdsWon.add(res.status === 201 ? 1 : 0);
  holdsLost.add(res.status === 409 ? 1 : 0);
  throttled.add(res.status === 429 ? 1 : 0);
}

export function teardown(data) {
  const stats = http.get(`${BASE_URL}/api/events/${data.eventId}/stats`).json();
  console.log(`Final stats: ${JSON.stringify(stats)}`);
  doubleBooked.add(stats.doubleBookedSeats);
  check(stats, {
    'zero double-bookings (whole run, incl. expired/cancelled)': (s) => s.doubleBookedSeats === 0,
    'never more seats held than exist': (s) => (s.seatsByStatus.HELD || 0) <= s.totalSeats,
  });
}
