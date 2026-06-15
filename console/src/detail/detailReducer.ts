import type { JobStatus } from "../types";

export type StreamEvent =
  | { type: "status"; jobId: string; status: JobStatus; attempt: number; ts: string }
  | {
      type: "metric";
      jobId: string;
      attempt: number;
      epoch: number;
      loss: number;
      accuracy: number;
      ts: string;
    };

export interface MetricPoint {
  epoch: number;
  loss: number;
  accuracy: number;
}

export interface DetailState {
  status: JobStatus | null;
  attempt: number | null;
  metricAttempt: number | null;
  points: MetricPoint[];
  logs: string[];
}

export const initialDetail: DetailState = {
  status: null,
  attempt: null,
  metricAttempt: null,
  points: [],
  logs: [],
};

const MAX_LOGS = 500;

function appendLog(logs: string[], line: string): string[] {
  const next = [...logs, line];
  return next.length > MAX_LOGS ? next.slice(-MAX_LOGS) : next;
}

export function detailReducer(state: DetailState, event: StreamEvent): DetailState {
  if (event.type === "status") {
    return {
      ...state,
      status: event.status,
      attempt: event.attempt,
      logs: appendLog(state.logs, `status → ${event.status} (attempt ${event.attempt})`),
    };
  }
  // metric: a retry starts a new attempt's curve, so reset points when the attempt advances
  const fresh = state.metricAttempt !== null && event.attempt > state.metricAttempt;
  const points = fresh ? [] : state.points;
  return {
    ...state,
    metricAttempt: event.attempt,
    points: [...points, { epoch: event.epoch, loss: event.loss, accuracy: event.accuracy }],
    logs: appendLog(
      state.logs,
      `epoch ${event.epoch}  loss ${event.loss.toFixed(4)}  acc ${event.accuracy.toFixed(4)}`,
    ),
  };
}
