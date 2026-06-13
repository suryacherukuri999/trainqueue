import json
import math
import os
import sys
import time


def env_int(name, default=None):
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    return int(raw)


def main():
    job_id = os.environ.get("JOB_ID", "unknown")
    epochs = env_int("EPOCHS", 5)
    fail_at = env_int("FAIL_AT_EPOCH")

    for epoch in range(1, epochs + 1):
        # Loss decays toward zero; accuracy climbs toward ~1. Deterministic so the
        # curve is reproducible across runs of the same job.
        loss = round(math.exp(-epoch / 3.0), 4)
        accuracy = round(1.0 - loss / 2.0, 4)
        print(json.dumps({
            "jobId": job_id,
            "epoch": epoch,
            "loss": loss,
            "accuracy": accuracy,
            "ts": time.time(),
        }), flush=True)

        if fail_at is not None and epoch == fail_at:
            print(f"injected failure at epoch {epoch}", file=sys.stderr, flush=True)
            sys.exit(1)

        time.sleep(2)

    sys.exit(0)


if __name__ == "__main__":
    main()
