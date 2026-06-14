# console

Vite + React 18 + TypeScript (strict). Two routes:

- **Jobs** (`/`) — submit form (name, epochs, priority, optional fail-at-epoch,
  max-retries) and a job table that polls `GET /api/jobs` every 3s with status
  badges and a cancel button.
- **Job detail** (`/jobs/:id`) — live status, streaming log lines, and a
  loss-vs-epoch chart fed over WebSocket (reconnect with backoff); plus persisted
  metrics, an artifact download link, and a log search box with a time filter.

## Run
Needs the api (`:8080`) and gateway (`:8081`) running.

```bash
npm install
npm run dev        # http://localhost:5173
```

`VITE_API_BASE` / `VITE_WS_URL` override the endpoints; the Docker build sets them
to same-origin `/api` and `/ws` (nginx proxies them).

## Test
```bash
npm test           # Vitest: job table render/cancel + detail-stream reducer
```
