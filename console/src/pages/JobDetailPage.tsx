import { Suspense, lazy, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getJob, getMetrics, searchLogs } from "../api";
import { StatusBadge } from "../components/StatusBadge";
import { useJobStream } from "../detail/useJobStream";
import type { MetricPoint } from "../detail/detailReducer";
import { TERMINAL } from "../types";
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
  const [savedPoints, setSavedPoints] = useState<MetricPoint[]>([]);
  const [savedLogs, setSavedLogs] = useState<string[]>([]);
  const { state, connected } = useJobStream(id);

  useEffect(() => {
    getJob(id)
      .then(setJob)
      .catch((e) => setError(e instanceof Error ? e.message : "failed to load job"));
  }, [id]);

  const status = state.status ?? job?.status ?? null;
  const terminal = status ? TERMINAL.has(status) : false;

  // A finished job has no live stream to replay, so load its persisted loss curve
  // (Mongo) and logs (Elasticsearch) so the panels aren't empty.
  useEffect(() => {
    if (!terminal) return;
    getMetrics(id)
      .then((m) =>
        setSavedPoints(m ? m.lossCurve.map((loss, i) => ({ epoch: i + 1, loss, accuracy: 0 })) : []),
      )
      .catch(() => undefined);
    searchLogs(id, "")
      .then((lines) =>
        setSavedLogs(lines.map((l) => `${new Date(l.ts).toLocaleTimeString()}  ${l.message}`)),
      )
      .catch(() => undefined);
  }, [id, terminal]);

  const points = state.points.length ? state.points : savedPoints;
  const logs = state.logs.length ? state.logs : savedLogs;

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
        {points.length === 0 ? (
          <p className="empty">{terminal ? "no loss data recorded for this run" : "waiting for epochs…"}</p>
        ) : (
          <Suspense fallback={<p className="empty">loading chart…</p>}>
            <LossChart points={points} />
          </Suspense>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <h2 className="card-title">{terminal ? "Logs" : "Live log"}</h2>
        </div>
        <pre className="log">
          {logs.length === 0
            ? terminal
              ? "no logs recorded"
              : "waiting for events…"
            : logs.join("\n")}
        </pre>
      </section>
    </section>
  );
}
