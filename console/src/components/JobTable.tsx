import { Link } from "react-router-dom";
import type { Job } from "../types";
import { TERMINAL } from "../types";
import { StatusBadge } from "./StatusBadge";

interface Props {
  jobs: Job[];
  onCancel: (id: string) => void;
}

export function JobTable({ jobs, onCancel }: Props) {
  if (jobs.length === 0) {
    return <p>No jobs yet. Submit one above.</p>;
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Status</th>
          <th>Epochs</th>
          <th>Priority</th>
          <th>Attempt</th>
          <th>Created</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {jobs.map((job) => (
          <tr key={job.id}>
            <td>
              <Link to={`/jobs/${job.id}`}>{job.name}</Link>
            </td>
            <td>
              <StatusBadge status={job.status} />
            </td>
            <td>{job.epochs}</td>
            <td>{job.priority}</td>
            <td>{job.attempt}</td>
            <td>{new Date(job.createdAt).toLocaleTimeString()}</td>
            <td>
              <button
                onClick={() => onCancel(job.id)}
                disabled={TERMINAL.has(job.status)}
              >
                Cancel
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
