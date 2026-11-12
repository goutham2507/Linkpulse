import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    redirect: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '30s', target: 100 },
        { duration: '5s', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_duration{name:redirect}': ['p(95)<500'],
    'http_req_failed{name:redirect}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const creds = JSON.stringify({ email: 'k6user@test.com', password: 'password123' });

  // may already exist from a previous run, so the result is ignored
  http.post(`${BASE}/auth/register`, creds, { headers: JSON_HEADERS });

  const login = http.post(`${BASE}/auth/login`, creds, { headers: JSON_HEADERS });
  if (login.status !== 200) {
    throw new Error(`login failed: ${login.status} ${login.body}`);
  }
  const token = login.json("accessToken");

  const codes = [];
  for (let i = 0; i < 10; i++) {
    const res = http.post(
      `${BASE}/v1/links`,
      JSON.stringify({ longUrl: `https://example.com/k6-target-${i}` }),
      { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } },
    );
    if (res.status !== 201) {
      throw new Error(`create link failed: ${res.status} ${res.body}`);
    }
    codes.push(res.json("code"));
  }
  return { codes };
}

export default function (data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  const res = http.get(`${BASE}/${code}`, {
    redirects: 0,
    tags: { name: 'redirect' },
  });
  check(res, { 'status is 302': (r) => r.status === 302 });
}
