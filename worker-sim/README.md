# worker-sim

Fake training run used as the job payload. Prints one JSON line per epoch
(`jobId`, `epoch`, `loss`, `accuracy`, `ts`) with a decaying loss, sleeping 2s
between epochs.

## Env
- `JOB_ID` — id echoed into each log line (default `unknown`)
- `EPOCHS` — number of epochs (default 5)
- `FAIL_AT_EPOCH` — optional; exit 1 at this epoch to simulate a failure

## Build & run
```bash
docker build -t worker-sim:latest .
docker run --rm -e JOB_ID=demo -e EPOCHS=5 worker-sim:latest
docker run --rm -e EPOCHS=5 -e FAIL_AT_EPOCH=3 worker-sim:latest   # exits 1
```
