# api

Spring Boot 3 / Java 21 REST API. Owns the `jobs` table (Flyway + Spring Data
JPA) and, in milestone 1 only, runs each submitted job as a worker-sim container
via a temporary `LocalDockerLauncher`. The scheduler service takes over execution
in milestone 2.

## Run
Needs Postgres (start it from the repo root with `docker compose up -d`,
published on host port **5433**) and the worker image built once:
`docker build -t worker-sim:latest ../worker-sim`.

```bash
./mvnw spring-boot:run        # http://localhost:8080
```

## Endpoints
- `POST /api/jobs` — submit a job. Body: `{"name","epochs","priority?","failAtEpoch?"}`.
- `GET  /api/jobs?status=` — list jobs, newest first; optional status filter.
- `GET  /api/jobs/{id}` — one job.
- `POST /api/jobs/{id}/cancel` — cancel a QUEUED/RUNNING job.

CORS is open to the console at `http://localhost:5173`.

## Test
```bash
./mvnw test     # status-transition unit tests + MockMvc controller tests
```
Tests use the web slice and pure unit tests, so they need neither Postgres nor Docker.
