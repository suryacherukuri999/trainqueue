import type { CreateJobRequest, Job } from "./types";

const BASE = "http://localhost:8080/api";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

export function listJobs(): Promise<Job[]> {
  return fetch(`${BASE}/jobs`).then((r) => json<Job[]>(r));
}

export function createJob(req: CreateJobRequest): Promise<Job> {
  return fetch(`${BASE}/jobs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  }).then((r) => json<Job>(r));
}

export function cancelJob(id: string): Promise<Job> {
  return fetch(`${BASE}/jobs/${id}/cancel`, { method: "POST" }).then((r) =>
    json<Job>(r),
  );
}
