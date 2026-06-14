# gateway

Node 20 + TypeScript WebSocket gateway. Browsers connect, name a job, and the
gateway streams that job's live events to them by subscribing to Redis pub/sub.

## How it works
- Client connects to `ws://localhost:8081/ws` and sends `{"subscribe":"<jobId>"}`.
- The gateway keeps one Redis subscription per job (`job:{id}:events`), opened on
  the first watcher and dropped when the last socket for that job closes, and
  fans each Redis message out to every open socket watching that job.
- `GET /healthz` returns `ok`.

Modules: `redis client + server wiring` (server.ts) and the testable
`subscriptionRegistry` (per-job socket sets + subscribe/fan-out/cleanup).

## Run
Needs Redis (`docker compose up -d` from the repo root).

```bash
npm install
npm run dev        # ws://localhost:8081/ws, http://localhost:8081/healthz
```

`PORT` and `REDIS_URL` are configurable via env.

## Test
```bash
npm test           # Jest: subscription registry + fan-out (mock ws + redis)
```
