# TrainQueue

Self-serve platform for launching and monitoring distributed ML training jobs.
Submit a job in the browser, a scheduler places and runs it in a container, and
its logs, status, and loss curve stream back live.

This is **milestone 3** — live streaming via Redis and a WebSocket gateway:

```
console (React) ──HTTP──▶ api (Spring Boot) ──▶ Postgres
       ▲                      │  ▲                  ▲
       │ WebSocket            │  │ jobs.status      │ cache-aside read
       │                jobs.submitted │            │
   gateway (Node/ws)          ▼  │              job:{id}:state (Redis)
       ▲                   Kafka  │                  ▲
       │ job:{id}:events          │                  │ SET state + PUBLISH events
       └──── Redis ◀──────── scheduler (Java) ──▶ worker-sim containers (Docker)
```

The scheduler streams each container's stdout to Redis (pub/sub for live events,
a cached snapshot for fast reads). The gateway fans those per-job event streams
out to browsers over WebSocket; the api serves `GET /api/jobs/{id}` from the Redis
snapshot first (cache-aside), falling back to Postgres.

## Services
- [api/](api/) — REST API + job table; publishes submissions, consumes status, cache-aside reads.
- [scheduler/](scheduler/) — placement, execution, retries, heartbeat, crash recovery, live streaming.
- [gateway/](gateway/) — Node 20 + TS WebSocket fan-out of Redis job event streams.
- [worker-sim/](worker-sim/) — Python training simulator (one JSON log line per epoch).
- [console/](console/) — Vite + React + TS dashboard with a live job detail page.

## Quickstart
Requires Docker Desktop **running**, Java 21, Node 20+, and Maven.

```bash
# 1. infra (postgres on host 5433, kafka on 9092, redis on 6379)
docker compose up -d

# 2. build the worker image the scheduler launches
docker build -t worker-sim:latest worker-sim

# 3. api  (terminal 1)
cd api && ./mvnw spring-boot:run            # http://localhost:8080

# 4. scheduler  (terminal 2)
cd scheduler && ./mvnw spring-boot:run

# 5. gateway  (terminal 3)
cd gateway && npm install && npm run dev    # ws://localhost:8081/ws

# 6. console  (terminal 4)
cd console && npm install && npm run dev    # http://localhost:5173
```

Submit a job, then click its name to open the detail page: status, streaming log
lines, and a loss-vs-epoch chart all update live over WebSocket.

## Verifying the interesting behaviour
- **Live stream** — open one job's detail page in **two browser tabs**; both stream identical lines and the chart animates in both.
- **Reconnect** — kill the gateway (`docker`/Ctrl-C) while watching; the page shows "reconnecting" and resumes when it's back.
- **Cache-aside** — while a job runs, `curl localhost:8080/api/jobs/<id>` and watch the api log a `cache hit`.
- **Capacity / retry / crash recovery** — see the scheduler README and earlier milestones.

## Tests
```bash
cd api && ./mvnw test         # transitions, state machine, controller
cd scheduler && ./mvnw test   # resource pool, ordering, retry policy
cd gateway && npm test        # subscription registry + fan-out
cd console && npm test        # job table render + detail reducer
```
