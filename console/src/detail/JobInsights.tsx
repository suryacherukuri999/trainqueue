import { useEffect, useState } from "react";
import { getArtifactUrl, getMetrics, searchLogs } from "../api";
import { TERMINAL } from "../types";
import type { JobStatus, LogLine, Metrics } from "../types";

function toEpochMs(local: string): number | undefined {
  if (!local) return undefined;
  const ms = new Date(local).getTime();
  return Number.isNaN(ms) ? undefined : ms;
}

/** Persisted metrics (Mongo), artifact download (S3), and log search (Elasticsearch). */
export function JobInsights({ id, status }: { id: string; status: JobStatus | null }) {
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [artifact, setArtifact] = useState<string | null>(null);

  const [q, setQ] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [results, setResults] = useState<LogLine[] | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);

  function refresh() {
    getMetrics(id).then(setMetrics).catch(() => setMetrics(null));
    getArtifactUrl(id).then(setArtifact).catch(() => setArtifact(null));
  }

  useEffect(refresh, [id]);

  // metrics/artifact appear only after completion, and the run doc can lag the status
  // flip — so on a terminal status, poll a few times until they show up.
  useEffect(() => {
    if (!status || !TERMINAL.has(status)) return;
    let tries = 0;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      const m = await getMetrics(id).catch(() => null);
      setMetrics(m);
      getArtifactUrl(id).then(setArtifact).catch(() => setArtifact(null));
      if (!m && tries++ < 5) timer = setTimeout(poll, 1000);
    };
    poll();
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, id]);

  async function runSearch(e: React.FormEvent) {
    e.preventDefault();
    try {
      setSearchError(null);
      setResults(await searchLogs(id, q.trim(), toEpochMs(from), toEpochMs(to)));
    } catch (err) {
      setSearchError(err instanceof Error ? err.message : "search failed");
    }
  }

  return (
    <>
      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Metrics</h2>
          <button type="button" onClick={refresh} className="link-btn">
            refresh
          </button>
        </div>
        {metrics ? (
          <p className="meta">
            final accuracy {metrics.finalAccuracy.toFixed(4)} · {metrics.epochs} epochs ·{" "}
            {(metrics.durationMs / 1000).toFixed(1)}s ·{" "}
            {artifact ? (
              <a href={artifact}>download artifact</a>
            ) : (
              <span className="conn">no artifact</span>
            )}
          </p>
        ) : (
          <p className="empty">no recorded run yet (available after the job completes)</p>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Log search</h2>
        </div>
        <form onSubmit={runSearch} className="submit-form">
          <label>
            Contains
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="e.g. epoch" />
          </label>
          <label>
            From
            <input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label>
            To
            <input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <button type="submit" className="btn-primary">
            Search
          </button>
        </form>
        {searchError && <p className="error">{searchError}</p>}
        {results && (
          <pre className="log">
            {results.length === 0
              ? "no matching log lines"
              : results
                  .map((l) => `${new Date(l.ts).toLocaleTimeString()}  ${l.message}`)
                  .join("\n")}
          </pre>
        )}
      </section>
    </>
  );
}
