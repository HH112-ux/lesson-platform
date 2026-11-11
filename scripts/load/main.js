// Load scenarios for the L1-10 performance gate.
//
// Runs against a local compose stack by default. Every input that cannot be
// derived from the API (published course ids, a lesson the demo buyer owns)
// is injected by scripts/load-preflight.sh, so this script never guesses ids.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.LP_LOAD_BASE_URL || 'http://localhost:8080';
const COURSE_IDS = splitIds(__ENV.LP_LOAD_COURSE_IDS);
const PAID_COURSE_IDS = splitIds(__ENV.LP_LOAD_PAID_COURSE_IDS);
const LESSON_ID = Number(__ENV.LP_LOAD_OWNED_LESSON_ID || 0);
const ACCOUNT = __ENV.LP_LOAD_ACCOUNT || 'demo_buyer';
const PASSWORD = __ENV.LP_LOAD_PASSWORD || 'Demo@123456';

const bizFailed = new Rate('biz_failed');

export const options = {
  scenarios: {
    course_detail: {
      executor: 'constant-vus',
      exec: 'courseDetail',
      vus: 10,
      duration: '60s',
    },
    course_chapters: {
      executor: 'constant-vus',
      exec: 'courseChapters',
      vus: 5,
      duration: '60s',
    },
    my_courses: {
      executor: 'constant-vus',
      exec: 'myCourses',
      vus: 3,
      duration: '60s',
    },
    progress_report: {
      executor: 'constant-vus',
      exec: 'progressReport',
      vus: 2,
      duration: '60s',
    },
    order_create_cancel: {
      executor: 'constant-vus',
      exec: 'orderCreateCancel',
      vus: 1,
      duration: '60s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.005'],
    biz_failed: ['rate<0.005'],
    'http_req_duration{scenario:course_detail}': ['p(95)<300'],
  },
};

let token = '';

export function setup() {
  if (COURSE_IDS.length === 0) {
    throw new Error('LP_LOAD_COURSE_IDS is empty; run scripts/load-preflight.sh first');
  }
  if (PAID_COURSE_IDS.length === 0) {
    throw new Error('LP_LOAD_PAID_COURSE_IDS is empty; needs a paid course the buyer does not own');
  }
  if (!LESSON_ID) {
    throw new Error('LP_LOAD_OWNED_LESSON_ID is empty; needs a lesson of a course the buyer owns');
  }
  const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({
    account: ACCOUNT,
    password: PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  const body = res.json();
  if (!body || body.code !== 0 || !body.data || !body.data.accessToken) {
    throw new Error(`login failed: HTTP ${res.status} code ${body && body.code}`);
  }
  token = body.data.accessToken;
  return { token };
}

export function courseDetail(data) {
  const id = pick(COURSE_IDS);
  const res = http.get(`${BASE}/api/v1/courses/${id}`, { tags: { name: 'course_detail' } });
  record(res);
  sleep(0.2);
}

export function courseChapters(data) {
  const id = pick(COURSE_IDS);
  const res = http.get(`${BASE}/api/v1/courses/${id}/chapters`, {
    tags: { name: 'course_chapters' },
  });
  record(res);
  sleep(0.3);
}

export function myCourses(data) {
  const res = apiGet('/api/v1/learning/my-courses?pageNum=1&pageSize=20', data.token);
  record(res);
  sleep(0.5);
}

export function progressReport(data) {
  const res = http.post(`${BASE}/api/v1/learning/progress`, JSON.stringify({
    lessonId: LESSON_ID,
    positionSeconds: 30,
  }), {
    headers: authHeaders(data.token),
    tags: { name: 'progress_report' },
  });
  record(res);
  sleep(1);
}

export function orderCreateCancel(data) {
  const courseId = pick(PAID_COURSE_IDS);
  const created = http.post(`${BASE}/api/v1/orders`, JSON.stringify({
    courseId: courseId,
    idempotencyKey: `load-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
  }), {
    headers: authHeaders(data.token),
    tags: { name: 'order_create' },
  });
  const createdBody = created.json();
  const ok = record(created);
  if (ok && createdBody && createdBody.data && createdBody.data.id) {
    const cancelled = http.post(`${BASE}/api/v1/orders/${createdBody.data.id}/cancel`,
      JSON.stringify({ reason: 'load test cleanup' }), {
        headers: authHeaders(data.token),
        tags: { name: 'order_cancel' },
      });
    record(cancelled);
  }
  // The sleep stays outside the early exit, otherwise a rejected create spins.
  sleep(1);
}

function apiGet(path, bearer) {
  return http.get(`${BASE}${path}`, {
    headers: authHeaders(bearer),
    tags: { name: path.split('?')[0] },
  });
}

function authHeaders(bearer) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${bearer}`,
  };
}

function record(res) {
  const body = res.json();
  const code = body && typeof body.code === 'number' ? body.code : -1;
  const ok = check(res, {
    'http 2xx': (r) => r.status >= 200 && r.status < 300,
    'business code 0': () => code === 0,
  });
  bizFailed.add(code !== 0);
  return ok;
}

function pick(ids) {
  return ids[Math.floor(Math.random() * ids.length)];
}

function splitIds(raw) {
  return (raw || '').split(',').map((v) => v.trim()).filter((v) => v.length > 0);
}
