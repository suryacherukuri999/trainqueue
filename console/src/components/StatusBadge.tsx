import type { JobStatus } from "../types";

export function StatusBadge({ status }: { status: JobStatus }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      <span className="dot" />
      {status}
    </span>
  );
}
