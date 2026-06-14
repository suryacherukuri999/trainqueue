# scheduler

Java 21 / Spring Boot worker (no web server). Owns job execution: it consumes
submissions from Kafka, places them when resources fit, runs each as a
worker-sim container, and reports status back on Kafka.

## What it does
- **Queue** — consumes `jobs.submitted` into an in-memory priority queue
  (priority desc, then oldest first).
- **Placement** — a configurable resource pool (default 4000 cpuMillis /
  8192 memMb). A job is placed only if it fits; its cpu/mem are released when it
  finishes. The highest-priority job holds the next free slot (priority
  head-of-line).
- **Execution** — launches worker-sim via docker-java, applying the job's cpu/mem
  as container limits, and watches each container for its exit code. Publishes
  `RUNNING` on start and `SUCCEEDED`/`FAILED` on exit to `jobs.status`.
- **Retries** — on a nonzero exit, if `attempt < maxRetries` it re-submits the
  job as `attempt+1` after exponential backoff; otherwise it publishes `FAILED`.
- **Heartbeat** — every 10s it inspects tracked containers; one that has vanished
  is treated as a failure and runs the retry path.
- **Live streaming** — it follows each container's stdout and, per metric line,
  `PUBLISH`es to Redis `job:{id}:events` (fanned out by the gateway) and `SET`s a
  cached snapshot at `job:{id}:state` (EX 1h) for the api's cache-aside read.
  Status changes go on the same channel.
- **Persistence on completion** — `/output` is bind-mounted into the worker; on
  success the model artifact is uploaded to `s3://trainqueue-artifacts/{jobId}/`,
  a run document (duration, epochs, loss curve, final accuracy) is written to
  MongoDB `trainqueue.runs`, and every log line is bulk-indexed into the
  Elasticsearch `job-logs` index via a buffered writer (flush by count or timer).
- **Crash recovery** — on startup it reads the api's `RUNNING` jobs and reconciles
  them against live containers: a container that survived the restart is
  re-adopted (re-attach the exit watcher); a job whose container is gone is
  re-queued. Containers are intentionally left running on shutdown so a restart
  can re-adopt them.

## Cancel: control topic, not the status topic
Cancellation is a **command** ("stop this job"), not an observed status. It
travels on a dedicated `jobs.control` topic rather than `jobs.status`. Keeping
commands off the status stream means the api's state machine — which treats
`jobs.status` as a monotonic record of what happened — never has to tell
"what we want" apart from "what occurred", and the scheduler can react to a
cancel (kill the container, drop a queued job) without that intent being
replayed as history. The api sets `CANCELLED` itself when it issues the command;
the scheduler just stops the work and suppresses any retry/FAILED for it.

## Run
Needs Postgres + Kafka (`docker compose up -d` from the repo root), the api
running (for startup reconcile), and the worker image built
(`docker build -t worker-sim:latest worker-sim`).

```bash
./mvnw spring-boot:run
# smaller pool, e.g. to see exactly 2 jobs run at once:
TRAINQUEUE_POOL_CPUMILLIS=2000 ./mvnw spring-boot:run
```

## Test
```bash
./mvnw test     # resource pool (fits/doesn't/frees), priority ordering, retry policy
```
Unit tests need neither Kafka nor Docker; the cross-service behaviour is exercised end to end (see the root README).
