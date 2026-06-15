import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { JobTable } from "./JobTable";
import type { Job } from "../types";

function job(overrides: Partial<Job>): Job {
  return {
    id: crypto.randomUUID(),
    name: "job",
    dockerImage: "worker-sim:latest",
    command: null,
    epochs: 5,
    failAtEpoch: null,
    priority: 1,
    cpuMillis: 1000,
    memMb: 1024,
    status: "QUEUED",
    attempt: 1,
    maxRetries: 0,
    createdAt: new Date().toISOString(),
    startedAt: null,
    finishedAt: null,
    ...overrides,
  };
}

describe("JobTable", () => {
  it("renders a row per job with name and status", () => {
    render(
      <JobTable
        jobs={[
          job({ name: "alpha", status: "RUNNING" }),
          job({ name: "beta", status: "SUCCEEDED" }),
        ]}
        onCancel={() => {}}
        onDelete={() => {}}
      />,
      { wrapper: MemoryRouter },
    );

    expect(screen.getByText("alpha")).toBeInTheDocument();
    expect(screen.getByText("beta")).toBeInTheDocument();
    expect(screen.getByText("RUNNING")).toBeInTheDocument();
    expect(screen.getByText("SUCCEEDED")).toBeInTheDocument();
  });

  it("disables cancel for terminal jobs and fires onCancel otherwise", () => {
    const onCancel = vi.fn();
    render(
      <JobTable
        jobs={[
          job({ id: "live", name: "alpha", status: "RUNNING" }),
          job({ id: "done", name: "beta", status: "SUCCEEDED" }),
        ]}
        onCancel={onCancel}
        onDelete={() => {}}
      />,
      { wrapper: MemoryRouter },
    );

    const buttons = screen.getAllByRole("button", { name: "Cancel" });
    expect(buttons[0]).toBeEnabled();
    expect(buttons[1]).toBeDisabled();

    fireEvent.click(buttons[0]);
    expect(onCancel).toHaveBeenCalledWith("live");
  });

  it("fires onDelete for a row", () => {
    const onDelete = vi.fn();
    render(
      <JobTable
        jobs={[job({ id: "dead", name: "alpha", status: "FAILED" })]}
        onCancel={() => {}}
        onDelete={onDelete}
      />,
      { wrapper: MemoryRouter },
    );

    fireEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(onDelete).toHaveBeenCalledWith("dead");
  });

  it("shows an empty message with no jobs", () => {
    render(<JobTable jobs={[]} onCancel={() => {}} onDelete={() => {}} />, {
      wrapper: MemoryRouter,
    });
    expect(screen.getByText(/no jobs yet/i)).toBeInTheDocument();
  });
});
