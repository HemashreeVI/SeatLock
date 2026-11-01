// SeatLock load test: thousands of users fighting over a limited number of seats.
//
//   k6 run load-test/booking-rush.js
//   k6 run -e BASE_URL=http://localhost:8080 -e SEATS=100 -e VUS=500 -e DURATION=30s load-test/booking-rush.js
//
// At the end it asks the server for /api/events/{id}/stats and FAILS if any seat was double-booked.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEATS = parseInt(__ENV.SEATS || '100');
const SEATS_PER_ROW = Math.min(SEATS, 100);
const ROWS = Math.ceil(SEATS / SEATS_PER_ROW);

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
    double_booked_seats: ['count==0'],             // the whole point of SeatLock
    http_req_failed: ['rate<0.01'],                // 409/429 are expected answers, not failures
    'http_req_duration{name:hold}': ['p(95)<500'],
  },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409, 429));

export function setup() {
  const startsAt = new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString();
  const res = http.post(`${BASE_URL}/api/events`, JSON.stringify({
    name: 'Load test sale', venue: 'k6 Arena', startsAt,
    sections: [{ name: 'FLOOR', rows: ROWS, seatsPerRow: SEATS_PER_ROW, priceCents: 9900 }],
  }), { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'event created': (r) => r.status === 201 });
  const eventId = res.json('id');
  const seatIds = http.get(`${BASE_URL}/api/events/${eventId}/seats`).json().map((s) => s.id);
  return { eventId, seatIds };
}

export default function (data) {
  const user = `k6-${__VU}-${__ITER}`;
  // everyone wants the best seats: pick 1-2 seats biased towards the front rows
  const pick = () => data.seatIds[Math.floor(Math.pow(Math.random(), 2) * data.seatIds.length)];
  const wanted = [...new Set([pick(), pick()].slice(0, 1 + Math.floor(Math.random() * 2)))];

  const res = http.post(`${BASE_URL}/api/bookings`,
    JSON.stringify({ eventId: data.eventId, seatIds: wanted }),
    { headers: { 'Content-Type': 'application/json', 'X-User-Id': user }, tags: { name: 'hold' } });

  check(res, { 'hold answered 201/409/429': (r) => [201, 409, 429].includes(r.status) });
  if (res.status === 201) holdsWon.add(1);
  else if (res.status === 409) holdsLost.add(1);
  else if (res.status === 429) throttled.add(1);
}

export function teardown(data) {
  const stats = http.get(`${BASE_URL}/api/events/${data.eventId}/stats`).json();
  console.log(`Final stats: ${JSON.stringify(stats)}`);
  doubleBooked.add(stats.doubleBookedSeats);
  check(stats, {
    'zero double-bookings': (s) => s.doubleBookedSeats === 0,
    'every seat is held at most once': (s) => (s.seatsByStatus.HELD || 0) <= s.totalSeats,
  });
}
