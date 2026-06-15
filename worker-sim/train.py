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

    # Write a dummy trained-model artifact.
    os.makedirs("/output", exist_ok=True)
    artifact = "/output/model.bin"
    with open(artifact, "wb") as f:
        f.write(b"trainqueue dummy model for " + job_id.encode())

    # Under Docker the scheduler harvests /output from the stopped container. Under
    # Kubernetes the pod's filesystem is gone before we could copy it, so when an
    # S3 target is configured the worker uploads its own artifact (best-effort).
    upload_artifact(job_id, artifact)

    sys.exit(0)


def upload_artifact(job_id, path):
    bucket = os.environ.get("ARTIFACT_S3_BUCKET")
    if not bucket:
        return
    try:
        import boto3

        s3 = boto3.client(
            "s3",
            endpoint_url=os.environ.get("ARTIFACT_S3_ENDPOINT") or None,
            region_name=os.environ.get("ARTIFACT_S3_REGION", "us-east-1"),
        )
        s3.upload_file(path, bucket, f"{job_id}/model.bin")
    except Exception as e:  # artifact upload is best-effort; a hiccup must not fail the run
        print(f"artifact upload failed: {e}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    main()
