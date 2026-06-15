import { describe, expect, it } from "vitest";
import { detailReducer, initialDetail, type StreamEvent } from "./detailReducer";

const JOB = "job-1";

function status(s: StreamEvent & { type: "status" }): StreamEvent {
  return s;
}

describe("detailReducer", () => {
  it("records status transitions and a log line", () => {
    const state = detailReducer(initialDetail, {
      type: "status",
      jobId: JOB,
      status: "RUNNING",
      attempt: 1,
      ts: "t",
    });
    expect(state.status).toBe("RUNNING");
    expect(state.attempt).toBe(1);
    expect(state.logs).toHaveLength(1);
    expect(state.logs[0]).toContain("RUNNING");
  });

  it("accumulates metric points in order and logs them", () => {
    let state = detailReducer(initialDetail, {
      type: "metric",
      jobId: JOB,
      attempt: 1,
      epoch: 1,
      loss: 0.7,
      accuracy: 0.6,
      ts: "t",
    });
    state = detailReducer(state, {
      type: "metric",
      jobId: JOB,
      attempt: 1,
      epoch: 2,
      loss: 0.5,
      accuracy: 0.75,
      ts: "t",
    });

    expect(state.points).toEqual([
      { epoch: 1, loss: 0.7, accuracy: 0.6 },
      { epoch: 2, loss: 0.5, accuracy: 0.75 },
    ]);
    expect(state.logs).toHaveLength(2);
    expect(state.logs[1]).toContain("epoch 2");
  });

  it("resets the curve when a retry advances the attempt", () => {
    let state = detailReducer(initialDetail, { type: "metric", jobId: JOB, attempt: 1, epoch: 1, loss: 0.7, accuracy: 0.6, ts: "t" });
    state = detailReducer(state, { type: "metric", jobId: JOB, attempt: 2, epoch: 1, loss: 0.9, accuracy: 0.5, ts: "t" });

    expect(state.metricAttempt).toBe(2);
    expect(state.points).toEqual([{ epoch: 1, loss: 0.9, accuracy: 0.5 }]);
  });

  it("ignores a straggler metric from an older attempt", () => {
    let state = detailReducer(initialDetail, { type: "metric", jobId: JOB, attempt: 2, epoch: 1, loss: 0.9, accuracy: 0.5, ts: "t" });
    const before = state;
    state = detailReducer(state, { type: "metric", jobId: JOB, attempt: 1, epoch: 5, loss: 0.1, accuracy: 0.95, ts: "t" });

    expect(state).toBe(before);
    expect(state.metricAttempt).toBe(2);
    expect(state.points).toEqual([{ epoch: 1, loss: 0.9, accuracy: 0.5 }]);
  });

  it("keeps status and metrics independent in one timeline", () => {
    let state = detailReducer(initialDetail, status({ type: "status", jobId: JOB, status: "RUNNING", attempt: 1, ts: "t" }));
    state = detailReducer(state, { type: "metric", jobId: JOB, attempt: 1, epoch: 1, loss: 0.7, accuracy: 0.6, ts: "t" });
    state = detailReducer(state, status({ type: "status", jobId: JOB, status: "SUCCEEDED", attempt: 1, ts: "t" }));

    expect(state.status).toBe("SUCCEEDED");
    expect(state.points).toHaveLength(1);
    expect(state.logs).toHaveLength(3);
  });
});
