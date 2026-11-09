/** Synthetic Android transport checks only. No credentials, external API, or real conversation. */
import http from 'node:http';
import { CLIENT_HEADER, validateChat } from './local-companion.mjs';

const DELAY_MESSAGE = '__transport_check_delay__';
const FIRST_MESSAGE = '할머니 집에 가';
const FIRST_REPLY = '할머니를 만나러 가는구나. 어떤 기분이야?';
const NEXT_MESSAGE = '보고 싶어서 신나';
const NEXT_REPLY = '보고 싶어서 신나는구나. 함께 가는 길이 즐겁겠어.';
const friend = {
  name: '시연 뭉치',
  appearance: '동그란 몸과 반짝이는 꼬리',
  memories: [{ source: '강', part: '꼬리', detail: '반짝이는 강물' }],
};
const firstHistory = [{ role: 'assistant', content: '오늘 어디 가?' }];
const continuedHistory = [
  { role: 'user', content: FIRST_MESSAGE },
  { role: 'assistant', content: FIRST_REPLY },
];
const stats = { accepted: 0, completed: 0, rejected: 0, delayedStarted: 0, delayedCompleted: 0, cancelled: 0 };
const same = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const send = (res, status, data) => {
  if (res.destroyed || res.writableEnded) return;
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(data));
};
const reject = (res, status = 400) => {
  stats.rejected += 1;
  send(res, status, { error: { code: 'fixture_rejected', message: '합성 연결 검사 요청만 허용됩니다.' } });
};

const server = http.createServer((req, res) => {
  if (req.headers['x-scenery-client'] !== CLIENT_HEADER || req.headers.origin !== undefined ||
      !['127.0.0.1:8791', 'localhost:8791'].includes(req.headers.host) ||
      !['127.0.0.1', '::ffff:127.0.0.1'].includes(req.socket.remoteAddress)) {
    req.resume(); return reject(res, 403);
  }
  if (req.method === 'GET' && req.url === '/health') {
    req.resume(); return send(res, 200, { ready: true, mode: 'adult-demo', model: 'transport-fixture' });
  }
  // Only counters are exposed. No utterances, names, history, or response text are retained.
  if (req.method === 'GET' && req.url === '/fixture/status') {
    req.resume(); return send(res, 200, stats);
  }
  if (req.method !== 'POST' || req.url !== '/chat') { req.resume(); return reject(res, 404); }
  if (req.headers['content-type']?.split(';')[0].trim().toLowerCase() !== 'application/json') {
    req.resume(); return reject(res, 415);
  }
  let size = 0;
  let failed = false;
  const chunks = [];
  req.on('data', (chunk) => {
    if (failed) return;
    size += chunk.length;
    if (size > 32 * 1024) { failed = true; chunks.length = 0; reject(res, 413); return; }
    chunks.push(chunk);
  });
  req.on('error', () => { failed = true; });
  req.on('end', () => {
    if (failed) return;
    let input;
    try { input = validateChat(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
    catch { return reject(res); }
    if (!same(input.friend, friend) || ![FIRST_MESSAGE, NEXT_MESSAGE, DELAY_MESSAGE].includes(input.message) ||
        !same(input.history, input.message === FIRST_MESSAGE ? firstHistory : continuedHistory)) return reject(res);
    stats.accepted += 1;
    if (input.message === DELAY_MESSAGE) {
      stats.delayedStarted += 1;
      let replied = false;
      const timer = setTimeout(() => {
        replied = true;
        stats.delayedCompleted += 1;
        stats.completed += 1;
        // source=openai exercises the production wire contract; this fixture never calls OpenAI.
        send(res, 200, { reply: '지연 응답은 화면에 전달되면 안 돼.', mood: 'neutral', source: 'openai' });
      }, 2000);
      res.once('close', () => {
        clearTimeout(timer);
        if (!replied) stats.cancelled += 1;
      });
      return;
    }
    stats.completed += 1;
    send(res, 200, {
      reply: input.message === FIRST_MESSAGE ? FIRST_REPLY : NEXT_REPLY,
      mood: input.message === FIRST_MESSAGE ? 'happy' : 'excited',
      source: 'openai',
    });
  });
});
server.headersTimeout = 5000;
server.requestTimeout = 10_000;
server.on('clientError', (_error, socket) => { if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n'); });
server.on('error', () => {
  process.stderr.write('합성 연결 검사 서버를 시작할 수 없습니다. 기존 8791 포트 서버를 확인해 주세요.\n');
  process.exitCode = 1;
});
server.listen(8791, '127.0.0.1', () => {
  process.stdout.write('검증 전용 합성 연결 서버: 127.0.0.1:8791. 실제 OpenAI 호출과 키 사용은 없습니다.\n');
});
const stop = () => { server.close(); server.closeAllConnections(); };
process.once('SIGINT', stop);
process.once('SIGTERM', stop);
