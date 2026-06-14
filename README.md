# TrainQueue

Self-serve platform for launching and monitoring distributed ML training jobs.
Submit a job in the browser; a scheduler places and runs it in a container; its
logs, status, and loss curve stream back live; and its logs, artifacts, and run
metrics are persisted for search and download.

This is **milestone 4** — search, artifacts, and metrics:

```
console (React) ──HTTP──▶ api (Spring Boot) ──▶ Postgres        (job records)
       ▲                      │  ▲  │  │  └────▶ Elasticsearch   (log search)
       │ WebSocket            │  │  │  └───────▶ MongoDB         (run metrics)
   gateway (Node/ws)          │  │  └──────────▶ S3 / LocalStack (artifacts, presigned)
       ▲                jobs.submitted │ jobs.status   ▲
       │ job:{id}:events          ▼  │               cache-aside (Redis)
       └──── Redis ◀──────── scheduler (Java) ──▶ worker-sim containers (Docker)
                                  │  └──▶ S3 (upload model.bin), Mongo (run doc), ES (bulk-index logs)
```

The scheduler runs jobs and, on completion, uploads the model artifact to S3,
writes a run document to MongoDB, and bulk-indexes every log line into
Elasticsearch. The api exposes metrics, a presigned artifact URL, and full-text
log search over those stores.

## Services
- [api/](api/) — REST API + job table; events, cache-aside, metrics/artifacts/logs queries.
- [scheduler/](scheduler/) — placement, execution, retries, heartbeat, recovery, streaming, persistence.
- [gateway/](gateway/) — Node 20 + TS WebSocket fan-out of Redis job event streams.
- [worker-sim/](worker-sim/) — Python training simulator; writes `/output/model.bin`.
- [console/](console/) — Vite + React + TS dashboard: live detail page + metrics, artifact, log search.

## Quickstart
Requires Docker Desktop **running**, Java 21, Node 20+, and Maven.

```bash
# 1. infra: postgres(5433) kafka(9092) redis(6379) mongodb(27017) localstack/s3(4566) elasticsearch(9200)
docker compose up -d

# 2. build the worker image the scheduler launches
docker build -t worker-sim:latest worker-sim

# 3. api / scheduler / gateway / console (separate terminals)
cd api && ./mvnw spring-boot:run
cd scheduler && ./mvnw spring-boot:run
cd gateway && npm install && npm run dev
cd console && npm install && npm run dev    # http://localhost:5173
```

Submit a job, open its detail page for the live stream, and once it completes use
the **Metrics**, **artifact download**, and **log search** controls.

## Verifying milestone 4
```bash
JOB=<a completed job id>
curl "localhost:8080/api/jobs/$JOB/metrics"            # final accuracy + loss curve (Mongo)
curl "localhost:8080/api/jobs/$JOB/artifacts"          # presigned S3 URL (LocalStack)
curl "localhost:8080/api/jobs/$JOB/logs?q=epoch"       # full-text log search (Elasticsearch)
curl "localhost:8080/api/jobs/$JOB/logs?q=epoch&from=<ms>&to=<ms>"   # narrowed by time
```
Earlier milestones (capacity, retry, crash recovery, live streaming) are covered in the service READMEs.

## Tests
```bash
cd api && ./mvnw test         # transitions, state machine, controller, ES query-builder
cd scheduler && ./mvnw test   # resource pool, ordering, retry policy, run-document mapper
cd gateway && npm test        # subscription registry + fan-out
cd console && npm test        # job table render + detail reducer
```
