# TrainQueue

Self-serve platform for launching and monitoring distributed ML training jobs.
Submit a job in the browser; a scheduler places it, a real container runs it, and
its status flows back through Kafka.

This is **milestone 2** — execution is decoupled from the API behind Kafka:

```
console (React) ──HTTP──▶ api (Spring Boot) ──▶ Postgres
                              │  ▲
              jobs.submitted  │  │  jobs.status            jobs.control (cancel)
                              ▼  │                                 │
                          Kafka  │                                 │
                              │  └─────────────────────────────────┤
                              ▼                                     ▼
                          scheduler (Java) ──▶ worker-sim containers (Docker)
```

The api persists a job and publishes it; the **scheduler** owns the queue,
resource-aware placement, execution, retries, heartbeats, and cancellation, and
reports status back. The api applies status through an idempotent state machine.

## Services
- [api/](api/) — REST API + job table; publishes submissions, consumes status.
- [scheduler/](scheduler/) — priority queue, placement, docker-java execution, retries, heartbeat, crash recovery.
- [worker-sim/](worker-sim/) — Python training simulator (one JSON log line per epoch).
- [console/](console/) — Vite + React + TypeScript dashboard.

## Quickstart
Requires Docker Desktop **running**, Java 21, Node 20+, and Maven.

```bash
# 1. infra (postgres on host 5433, kafka on 9092)
docker compose up -d

# 2. build the worker image the scheduler launches
docker build -t worker-sim:latest worker-sim

# 3. api  (terminal 1)
cd api && ./mvnw spring-boot:run            # http://localhost:8080

# 4. scheduler  (terminal 2)
cd scheduler && ./mvnw spring-boot:run

# 5. console  (terminal 3)
cd console && npm install && npm run dev    # http://localhost:5173
```

Submit a job and watch `QUEUED → RUNNING → SUCCEEDED`. Set "fail at epoch" with
"max retries" > 0 to watch it retry then end `FAILED`. Cancel a running job to
see its container stop.

## Verifying the interesting behaviour
- **Capacity** — run the scheduler with a small pool and submit several jobs:
  ```bash
  cd scheduler && TRAINQUEUE_POOL_CPUMILLIS=2000 ./mvnw spring-boot:run
  ```
  With 1000-cpuMillis jobs, exactly 2 run at once (`docker ps`); the rest stay `QUEUED`.
- **Retry** — submit with `failAtEpoch` set and `maxRetries` > 0; it fails, re-runs, and finally ends `FAILED`.
- **Crash recovery** — start a long job (`epochs: 30`), Ctrl-C the scheduler, restart it; it reconciles against the api and re-adopts or re-queues the job.
- **Watch Kafka**:
  ```bash
  docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic jobs.status --from-beginning
  ```

## Tests
```bash
cd api && ./mvnw test         # transitions, state machine, controller
cd scheduler && ./mvnw test   # resource pool, ordering, retry policy
cd console && npm test        # job table render
```
