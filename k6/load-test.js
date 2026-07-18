import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    payment_load: {
      executor: 'constant-arrival-rate',
      rate: 100,             // Section B3: 100 req/sec
      timeUnit: '1s',
      duration: '5m',        // Section B3: sustained for 5 minutes
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],  // Section B3: Payment Initiation P95 < 500ms
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'dev-api-key-change-me';

export default function () {
  const payload = JSON.stringify({
    merchantId: uuidv4(),
    merchantOrderId: `ORD-${uuidv4()}`,
    amountPaise: 100000,
    currency: 'INR',
    paymentMethod: 'UPI',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'Idempotency-Key': uuidv4(),
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'state is AUTHORISED': (r) => JSON.parse(r.body).state === 'AUTHORISED',
  });
}