import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getJob } from "../api";
import { StatusBadge } from "../components/StatusBadge";
import { JobInsights } from "../detail/JobInsights";
import { useJobStream } from "../detail/useJobStream";
import type { Job } from "../types";

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
        <Link to="/">← all jobs</Link>
      </p>
      <h2>
        {job?.name ?? id}{" "}
        {status && <StatusBadge status={status} />}{" "}
        <span className="conn">{connected ? "● live" : "○ reconnecting"}</span>
      </h2>
      {error && <p className="error">{error}</p>}
      {job && (
        <p className="meta">
          image {job.dockerImage} · epochs {job.epochs} · priority {job.priority} · attempt{" "}
          {state.attempt ?? job.attempt} · maxRetries {job.maxRetries}
        </p>
      )}

      <h3>Loss</h3>
      <div style={{ width: "100%", height: 260 }}>
        <ResponsiveContainer>
          <LineChart data={state.points}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="epoch" />
            <YAxis domain={[0, "auto"]} />
            <Tooltip />
            <Line type="monotone" dataKey="loss" stroke="#2563eb" dot={false} isAnimationActive />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <h3>Live log</h3>
      <pre className="log">
        {state.logs.length === 0 ? "waiting for events…" : state.logs.join("\n")}
      </pre>

      <JobInsights id={id} />
    </section>
  );
}
