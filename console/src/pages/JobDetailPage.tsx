import { Suspense, lazy, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getJob } from "../api";
import { StatusBadge } from "../components/StatusBadge";
import { JobInsights } from "../detail/JobInsights";
import { useJobStream } from "../detail/useJobStream";
import type { Job } from "../types";

const LossChart = lazy(() => import("../detail/LossChart"));

export function JobDetailPage() {
  const { id } = useParams();
  if (!id) return <p>Unknown job.</p>;
  // key by id so navigating between jobs resets the stream state
  return <JobDetail key={id} id={id} />;
}

function JobDetail({ id }: { id: string }) {
  const [job, setJob] = useState<Job | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { state, connected } = useJobStream(id);

  useEffect(() => {
    getJob(id)
      .then(setJob)
      .catch((e) => setError(e instanceof Error ? e.message : "failed to load job"));
  }, [id]);

  const status = state.status ?? job?.status ?? null;

  return (
    <section>
      <p>
        <Link to="/" className="back-link">
          ← all jobs
        </Link>
      </p>

      <section className="card">
        <div className="detail-head">
          <h2>{job?.name ?? id}</h2>
          {status && <StatusBadge status={status} />}
          <span className={connected ? "conn live" : "conn"}>
            {connected ? "● live" : "○ reconnecting"}
          </span>
        </div>
        {error && <p className="error">{error}</p>}
        {job && (
          <p className="meta">
            image {job.dockerImage} · epochs {job.epochs} · priority {job.priority} · attempt{" "}
            {state.attempt ?? job.attempt} · maxRetries {job.maxRetries}
          </p>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Loss curve</h2>
        </div>
        <Suspense fallback={<p className="empty">loading chart…</p>}>
          <LossChart points={state.points} />
        </Suspense>
      </section>

      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Live log</h2>
        </div>
        <pre className="log">
          {state.logs.length === 0 ? "waiting for events…" : state.logs.join("\n")}
        </pre>
      </section>

      <JobInsights id={id} status={status} />
    </section>
  );
}
