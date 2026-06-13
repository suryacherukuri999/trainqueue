import type { JobStatus } from "../types";

const COLORS: Record<JobStatus, string> = {
  QUEUED: "#6b7280",
  RUNNING: "#2563eb",
  SUCCEEDED: "#16a34a",
  FAILED: "#dc2626",
  CANCELLED: "#a16207",
};

export function StatusBadge({ status }: { status: JobStatus }) {
  return (
    <span
      style={{
        background: COLORS[status],
        color: "white",
        borderRadius: 4,
        padding: "2px 8px",
        fontSize: 12,
        fontWeight: 600,
      }}
    >
      {status}
    </span>
  );
}
