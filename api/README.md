# api

Spring Boot 3 / Java 21 REST API. Owns the `jobs` table (Flyway + Spring Data
JPA) and the job lifecycle as seen by clients. It no longer runs jobs itself:
on submit it publishes a `JobSubmittedEvent` to Kafka and the scheduler executes
it. The api consumes `jobs.status` and applies updates through a state machine.

## Run
Needs Postgres + Kafka (start them from the repo root with `docker compose up -d`;
Postgres is published on host port **5433**).

```bash
./mvnw spring-boot:run        # http://localhost:8080
```

## Endpoints
- `POST /api/jobs` — submit a job. Body: `{"name","epochs","priority?","failAtEpoch?","maxRetries?"}`.
  Persists `QUEUED` and publishes to `jobs.submitted` after the transaction commits.
- `GET  /api/jobs?status=` — list jobs, newest first; optional status filter.
- `GET  /api/jobs/{id}` — one job.
- `POST /api/jobs/{id}/cancel` — set `CANCELLED` and publish a cancel command to `jobs.control`.
- `GET  /api/jobs/{id}/metrics` — final accuracy + loss curve for the latest attempt (MongoDB).
- `GET  /api/jobs/{id}/artifacts` — presigned S3 URL for the model artifact (404 if none).
- `GET  /api/jobs/{id}/logs?q=&from=&to=` — full-text log search with a time window (Elasticsearch).

## Cache-aside reads
`GET /api/jobs/{id}` reads the scheduler-maintained `job:{id}:state` snapshot from
Redis first (logging a `cache hit`), and falls back to Postgres on a miss. Cancel
evicts the key so the next read reflects `CANCELLED` from the database.

## State machine
`JobStateMachine` applies `jobs.status` events. Only forward progress is accepted,
ordered by `(attempt, state)` — this enforces legal transitions and makes the
application idempotent against duplicate and out-of-order events (a replayed
`RUNNING` or a late event for an older attempt ranks no higher than current state
and is dropped). Higher attempts win, which is how a retry resumes a job.

CORS is open to the console at `http://localhost:5173`.

## Test
```bash
./mvnw test     # status-transition + state-machine unit tests + MockMvc controller tests
```
Tests need neither Postgres, Kafka, nor Docker.
