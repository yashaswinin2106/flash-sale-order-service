// Flash sale without the reservation step: every buyer calls POST /orders directly.
// Used to compare the Postgres stock strategies.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BUYERS = Number(__ENV.BUYERS || 5000);
const VUS = Number(__ENV.VUS || 200);

const created = new Counter('orders_created');
const soldOut = new Counter('orders_sold_out');
const otherErrors = new Counter('orders_other_errors');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    buyers: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: BUYERS,
      maxDuration: '5m',
    },
  },
};

export default function () {
  const userId = `user-${__VU}-${__ITER}`;
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ productId: 1 }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': userId,
        'Idempotency-Key': `${userId}-${Date.now()}-${Math.random()}`,
      },
      responseCallback: http.expectedStatuses(201, 202, 409),
    },
  );

  if (res.status === 201 || res.status === 202) {
    created.add(1);
  } else if (res.status === 409) {
    soldOut.add(1);
  } else {
    otherErrors.add(1);
  }
  check(res, { 'order accepted or sold out': (r) => [201, 202, 409].includes(r.status) });
}
