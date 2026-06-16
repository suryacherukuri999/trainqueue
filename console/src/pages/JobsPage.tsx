import { useCallback, useEffect, useState } from "react";
import { cancelJob, createJob, deleteAllJobs, deleteJob, listJobs } from "../api";
import { JobTable } from "../components/JobTable";
import { SubmitForm } from "../components/SubmitForm";
import type { CreateJobRequest, Job } from "../types";

const POLL_MS = 3000;

export function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [error, setError] = useState<string | null>(null);

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
      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Submit a job</h2>
        </div>
        <SubmitForm onSubmit={handleSubmit} />
      </section>

      {error && <p className="error">{error}</p>}

      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Jobs{jobs.length > 0 && ` · ${jobs.length}`}</h2>
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
