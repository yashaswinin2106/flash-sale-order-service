// Full flash sale: every buyer reserves, orders with the reservation, then polls until the
// payment settles. Most buyers stop at the reservation with 409 sold out.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BUYERS = Number(__ENV.BUYERS || 5000);
const VUS = Number(__ENV.VUS || 200);
const POLL_TIMEOUT_S = Number(__ENV.POLL_TIMEOUT_S || 30);

const reserved = new Counter('reservations_created');
const soldOut = new Counter('reservations_sold_out');
const ordersAccepted = new Counter('orders_accepted');
const confirmed = new Counter('orders_confirmed');
const failed = new Counter('orders_failed');
const unsettled = new Counter('orders_unsettled');
const otherErrors = new Counter('other_errors');
const timeToSettle = new Trend('time_to_settle', true);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  // Thresholds on tagged sub-metrics make k6 report latency per step in the summary.
  thresholds: {
    'http_req_duration{step:reserve}': ['p(99)<10000'],
    'http_req_duration{step:order}': ['p(99)<10000'],
    'http_req_duration{step:poll}': ['p(99)<10000'],
  },
  scenarios: {
    buyers: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: BUYERS,
      maxDuration: '10m',
    },
  },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export default function () {
  const userId = `user-${__VU}-${__ITER}`;
  const json = { 'Content-Type': 'application/json', 'X-User-Id': userId };

  const reservation = http.post(`${BASE_URL}/api/v1/reservations`, JSON.stringify({ productId: 1 }), {
    headers: json,
    tags: { step: 'reserve' },
    responseCallback: http.expectedStatuses(200, 201, 409),
  });
  if (reservation.status === 409) {
    soldOut.add(1);
    return;
  }
  if (!check(reservation, { 'reserved': (r) => r.status === 201 || r.status === 200 })) {
    otherErrors.add(1);
    return;
  }
  reserved.add(1);

  // The same key is reused on retry, so a timed-out request cannot create a second order.
  const idempotencyKey = uuid();
  const body = JSON.stringify({ reservationId: reservation.json('reservationId') });
  let order;
  for (let attempt = 0; attempt < 3; attempt++) {
    order = http.post(`${BASE_URL}/api/v1/orders`, body, {
      headers: { ...json, 'Idempotency-Key': idempotencyKey },
      tags: { step: 'order' },
      responseCallback: http.expectedStatuses(202),
    });
    if (order.status === 202) {
      break;
    }
  }
  if (!check(order, { 'order accepted': (r) => r.status === 202 })) {
    otherErrors.add(1);
    return;
  }
  ordersAccepted.add(1);

  const orderId = order.json('orderId');
  const started = Date.now();
  while ((Date.now() - started) / 1000 < POLL_TIMEOUT_S) {
    sleep(0.2);
    const status = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, {
      headers: { 'X-User-Id': userId },
      tags: { step: 'poll' },
    }).json('status');
    if (status === 'CONFIRMED' || status === 'FAILED') {
      timeToSettle.add(Date.now() - started);
      (status === 'CONFIRMED' ? confirmed : failed).add(1);
      return;
    }
  }
  unsettled.add(1);
}
