export type JobStatus =
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED";

export interface Job {
  id: string;
  name: string;
  dockerImage: string;
  command: string | null;
  epochs: number;
  failAtEpoch: number | null;
  priority: number;
  cpuMillis: number;
  memMb: number;
  status: JobStatus;
  attempt: number;
  maxRetries: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface CreateJobRequest {
  name: string;
  epochs: number;
  priority: number;
  failAtEpoch?: number;
  maxRetries?: number;
}

export const TERMINAL: ReadonlySet<JobStatus> = new Set([
  "SUCCEEDED",
  "FAILED",
  "CANCELLED",
]);
