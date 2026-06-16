import { Link } from "react-router-dom";
import type { Job } from "../types";
import { TERMINAL } from "../types";
import { StatusBadge } from "./StatusBadge";

interface Props {
  jobs: Job[];
  onCancel: (id: string) => void;
  onDelete: (id: string) => void;
}

export function JobTable({ jobs, onCancel, onDelete }: Props) {
  if (jobs.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-icon" aria-hidden="true">
          +
        </div>
        <h3>No jobs in the queue</h3>
        <p>Submit a training job above and it will appear here with live status updates.</p>
      </div>
    );
  }

  return (
    <div className="table-shell">
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
            <tr key={job.id} className={`job-row status-${job.status.toLowerCase()}`}>
              <td>
                <Link className="job-link" to={`/jobs/${job.id}`}>
                  {job.name}
                </Link>
              </td>
              <td>
                <StatusBadge status={job.status} />
              </td>
              <td>{job.epochs}</td>
              <td>{job.priority}</td>
              <td>{job.attempt}</td>
              <td>{new Date(job.createdAt).toLocaleTimeString()}</td>
              <td>
                <div className="row-actions">
                  <button
                    onClick={() => onCancel(job.id)}
                    disabled={TERMINAL.has(job.status)}
                  >
                    Cancel
                  </button>
                  <button className="danger-button" onClick={() => onDelete(job.id)}>
                    Delete
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
