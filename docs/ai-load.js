import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// ─────────────────────────────────────────────────────────────────────────────
// Nocteon AI load check.
//
//   k6 run --scenario search docs/ai-load.js     # 200 requests @ 10 VUs
//   k6 run --scenario chat   docs/ai-load.js     # 50 VUs for 2 minutes
//   k6 run docs/ai-load.js                       # both, sequentially
//
// Targets, from spec §13. These are the project's targets, not results:
//   search  p95 < 200 ms
//   chat    p95 time-to-reply < 3 s, 5xx under 1%
//
// The chat target has NOT been met on anything measured so far. On local
// qwen2.5:3b-instruct (AMD Ryzen 5 4600H) it was 48,958 ms p95 at 50 concurrent —
// 16x over. See docs/ai-golden-queries.md for the full numbers and the hardware.
//
// Which model this measures depends entirely on your environment: with GROQ_API_KEY set
// it hits Groq, without it hits local Ollama. State which one when you report a result —
// the difference between them is two orders of magnitude. The Groq path has never been
// successfully exercised, so treat its first run as the measurement rather than a
// regression check.
// ─────────────────────────────────────────────────────────────────────────────

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

// time-to-reply, which http_req_duration cannot isolate: for chat the number a shopper
// feels is how long the bubble takes to appear, not how long the model streamed.
const chatReplyDuration = new Trend('chat_reply_duration', true);

// A 200 carrying the degraded "our coffee guide is taking a short break" apology is not a
// success — it is the endpoint admitting the model produced nothing. Counting it as one
// would hide exactly the failure this check exists to catch.
const chatDegradedReply = new Rate('chat_degraded_reply');

const chatFiveHundreds = new Rate('chat_5xx');

export const options = {
  scenarios: {
    search: {
      exec: 'searchScenario',
      vus: 10,
      iterations: 200,
      thresholds: {
        http_req_duration: ['p(95)<200'],
        http_req_failed: ['rate<0.01'],
      },
    },
    chat: {
      exec: 'chatScenario',
      vus: 50,
      duration: '2m',
      thresholds: {
        chat_reply_duration: ['p(95)<3000'],
        chat_5xx: ['rate<0.01'],
      },
    },
  },
};

// The ten English and ten Arabic golden queries, so every run replays the same traffic and
// a ranking regression shows up as a latency regression rather than as noise.
const GOLDEN_QUERIES = [
  'fruity light roast',
  'chocolatey dark roast',
  'ethiopian coffee',
  'best for espresso',
  'sweet and floral',
  'coffee for pour over',
  'fruity and tea-like',
  'bold and smoky',
  'coffee with caramel notes',
  'bright and citrusy',
  'قهوة إثيوبية',
  'تحميص فاتح',
  'قهوة بالشوكولاتة',
  'مناسبة للإسبريسو',
  'قهوة زهرية',
  'قهوة مقطّرة',
  'محمصة غامقة',
  'نكهة فواكه',
  'قهوة برية',
  'قهوة خفيفة',
];

const CHAT_QUESTIONS = [
  'What coffee for a V60 pour over?',
  'I want a gift for someone who loves espresso',
  'What does washed processing mean?',
  'اقترح لي قهوة مناسبة للإسبريسو',
  "What's the status of my order?",
];

function isArabic(text) {
  return /[؀-ۿ]/.test(text);
}

// RateLimitFilter keys anonymous requests on X-Real-IP (falling back to remoteAddr).
// Without a per-VU address a load run measures the 200-requests-per-minute-per-IP
// limiter instead of the endpoint under test, and reports a p95 of single-digit
// milliseconds for a stream of 429s.
function perVuAddress() {
  return `10.${Math.floor(__VU / 250)}.${(__VU % 250) + 1}`;
}

// ── scenario: semantic search ─────────────────────────────────────────────────
export function searchScenario() {
  const query = GOLDEN_QUERIES[(__VU + __ITER) % GOLDEN_QUERIES.length];

  const res = http.get(
    `${BASE}/api/products?semantic=true&size=10&page=0&search=${encodeURIComponent(query)}`,
    {
      headers: {
        Accept: 'application/json',
        'Accept-Language': isArabic(query) ? 'ar' : 'en',
        'X-Real-IP': perVuAddress(),
      },
      tags: { endpoint: 'products-semantic' },
    },
  );

  check(res, {
    'search: 200': (r) => r.status === 200,
    'search: not rate limited': (r) => r.status !== 429,
  });
}

// ── scenario: chat ────────────────────────────────────────────────────────────
export function chatScenario() {
  const question = CHAT_QUESTIONS[(__VU + __ITER) % CHAT_QUESTIONS.length];
  const arabic = isArabic(question);

  const res = http.post(
    `${BASE}/api/ai/chat`,
    JSON.stringify({
      message: question,
      sessionId: `k6-${__VU}-${__ITER}`,
      locale: arabic ? 'ar' : 'en',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'Accept-Language': arabic ? 'ar' : 'en',
        'X-Real-IP': perVuAddress(),
      },
      tags: { endpoint: 'ai-chat' },
    },
  );

  check(res, {
    'chat: 200': (r) => r.status === 200,
    'chat: not rate limited': (r) => r.status !== 429,
    'chat: has a reply': (r) => !!(r.json('data.reply') || '').trim(),
  });

  const reply = res.json('data.reply') || '';
  chatReplyDuration.add(res.timings.duration);
  chatFiveHundreds.add(res.status >= 500);
  chatDegradedReply.add(reply.includes('short break') || reply.includes('إجازة قصيرة'));
}

export function teardown() {
  console.log('');
  console.log('Reading this run:');
  console.log('  * chat_degraded_reply above 0 means turns that returned a 200 carrying the');
  console.log('    "our coffee guide is taking a short break" apology. Not a pass.');
  console.log('  * "chat: has a reply" passing does not mean the reply is truthful. Every slug');
  console.log('    the model names must be cross-checked against GET /api/products; see');
  console.log('    docs/ai-golden-queries.md for the fabrication rate measured on qwen2.5:3b.');
}