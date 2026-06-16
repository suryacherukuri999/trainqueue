import { useCallback, useEffect, useState } from "react";
import { cancelJob, createJob, deleteAllJobs, deleteJob, listJobs } from "../api";
import { JobTable } from "../components/JobTable";
import { SubmitForm } from "../components/SubmitForm";
import type { CreateJobRequest, Job } from "../types";

const POLL_MS = 3000;

export function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [error, setError] = useState<string | null>(null);
  const running = jobs.filter((job) => job.status === "RUNNING").length;
  const queued = jobs.filter((job) => job.status === "QUEUED").length;
  const succeeded = jobs.filter((job) => job.status === "SUCCEEDED").length;
  const failed = jobs.filter((job) => job.status === "FAILED").length;
  const active = running + queued;

  const refresh = useCallback(async () => {
    try {
      setJobs(await listJobs());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to load jobs");
    }
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, POLL_MS);
    return () => clearInterval(id);
  }, [refresh]);

  async function handleSubmit(req: CreateJobRequest) {
    try {
      await createJob(req);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to submit job");
    }
  }

  async function handleCancel(id: string) {
    try {
      await cancelJob(id);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to cancel job");
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteJob(id);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to delete job");
    }
  }

  async function handleDeleteAll() {
    if (jobs.length === 0) return;
    if (!window.confirm("Delete all jobs?")) return;
    try {
      await deleteAllJobs();
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "failed to delete jobs");
    }
  }

  return (
    <>
      <section className="hero">
        <div>
          <p className="eyebrow">Distributed training queue</p>
          <h1>Launch, watch, and manage ML jobs from one control panel.</h1>
          <p className="hero-copy">
            Submit simulated training runs, track retries and terminal states, inspect logs,
            and clean up finished jobs when your demo board gets busy.
          </p>
        </div>
        <div className="hero-orb" aria-hidden="true">
          <span />
        </div>
      </section>

      <section className="stats-grid" aria-label="Queue summary">
        <div className="stat-card">
          <span>Total jobs</span>
          <strong>{jobs.length}</strong>
        </div>
        <div className="stat-card">
          <span>Active</span>
          <strong>{active}</strong>
          <small>{running} running · {queued} queued</small>
        </div>
        <div className="stat-card">
          <span>Succeeded</span>
          <strong>{succeeded}</strong>
        </div>
        <div className="stat-card danger-stat">
          <span>Failed</span>
          <strong>{failed}</strong>
        </div>
      </section>

      <section className="card submit-card">
        <div className="card-head stacked-head">
          <div>
            <p className="eyebrow">New run</p>
            <h2 className="card-title">Submit a job</h2>
          </div>
          <p className="card-note">Blank numeric fields use the suggested defaults.</p>
        </div>
        <SubmitForm onSubmit={handleSubmit} />
      </section>

      {error && <p className="error">{error}</p>}

      <section className="card jobs-card">
        <div className="card-head jobs-head">
          <div>
            <p className="eyebrow">Live queue</p>
            <h2 className="card-title">Jobs{jobs.length > 0 && ` · ${jobs.length}`}</h2>
          </div>
          <button
            className="danger-button"
            onClick={handleDeleteAll}
            disabled={jobs.length === 0}
          >
            Delete all
          </button>
        </div>
        <JobTable jobs={jobs} onCancel={handleCancel} onDelete={handleDelete} />
      </section>
    </>
  );
}
