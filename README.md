# TrainQueue

Self-serve platform for launching and monitoring distributed ML training jobs.
Submit a job in the browser, a real container runs it, and you watch it reach
`SUCCEEDED`.

This is **milestone 1** — a working end-to-end skeleton:

```
console (React) ──HTTP──▶ api (Spring Boot) ──▶ Postgres
                              │
                              └── runs worker-sim as a Docker container
```

The api currently launches jobs itself via a temporary `LocalDockerLauncher`. A
dedicated Kafka-driven scheduler takes over execution in milestone 2.

## Services
- [api/](api/) — Spring Boot 3 / Java 21 REST API; owns the `jobs` table.
- [worker-sim/](worker-sim/) — Python training simulator (one JSON log line per epoch).
- [console/](console/) — Vite + React + TypeScript dashboard.

## Quickstart
Requires Docker Desktop **running**, Java 21, Node 20+, and Maven.

```bash
# 1. infra (postgres, published on host port 5433)
docker compose up -d

# 2. build the worker image the api launches
docker build -t worker-sim:latest worker-sim

# 3. api  (terminal 1)
cd api && ./mvnw spring-boot:run        # http://localhost:8080

# 4. console  (terminal 2)
cd console && npm install && npm run dev # http://localhost:5173
```

Open http://localhost:5173, submit a job, and watch it go
`QUEUED → RUNNING → SUCCEEDED`. While it runs you can see the container in
`docker ps`. Set "fail at epoch" to watch a job end `FAILED`.

## Tests
```bash
cd api && ./mvnw test       # JUnit: status transitions + controller
cd console && npm test      # Vitest: job table render
```
