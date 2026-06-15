import type { CreateJobRequest, Job, LogLine, Metrics } from "./types";

// Same-origin "/api" in the deployed build (nginx proxies it); localhost in dev.
const BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080/api";
const API_KEY = import.meta.env.VITE_API_KEY ?? "dev-key";

function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${BASE}${path}`, {
    ...init,
    headers: { "X-API-Key": API_KEY, ...(init.headers ?? {}) },
  });
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

export function listJobs(): Promise<Job[]> {
  return apiFetch("/jobs").then((r) => json<Job[]>(r));
}

export function getJob(id: string): Promise<Job> {
  return apiFetch(`/jobs/${id}`).then((r) => json<Job>(r));
}

export function createJob(req: CreateJobRequest): Promise<Job> {
  return apiFetch("/jobs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  }).then((r) => json<Job>(r));
}

export function cancelJob(id: string): Promise<Job> {
  return apiFetch(`/jobs/${id}/cancel`, { method: "POST" }).then((r) => json<Job>(r));
}

async function empty(res: Response): Promise<void> {
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`);
  }
}

export function deleteJob(id: string): Promise<void> {
  return apiFetch(`/jobs/${id}`, { method: "DELETE" }).then(empty);
}

export function deleteAllJobs(): Promise<void> {
  return apiFetch("/jobs", { method: "DELETE" }).then(empty);
}

export async function getMetrics(id: string): Promise<Metrics | null> {
  const res = await apiFetch(`/jobs/${id}/metrics`);
  if (res.status === 404) return null;
  return json<Metrics>(res);
}

export async function getArtifactUrl(id: string): Promise<string | null> {
  const res = await apiFetch(`/jobs/${id}/artifacts`);
  if (res.status === 404) return null;
  const body = await json<{ url: string }>(res);
  return body.url;
}

export function searchLogs(
  id: string,
  q: string,
  from?: number,
  to?: number,
): Promise<LogLine[]> {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (from !== undefined) params.set("from", String(from));
  if (to !== undefined) params.set("to", String(to));
  const qs = params.toString();
  return apiFetch(`/jobs/${id}/logs${qs ? `?${qs}` : ""}`).then((r) => json<LogLine[]>(r));
}
