import { fireEvent, render, screen } from "@testing-library/react";
import { SubmitForm } from "./SubmitForm";

describe("SubmitForm", () => {
  it("shows numeric defaults as placeholders and submits defaults when left blank", () => {
    const onSubmit = vi.fn();
    render(<SubmitForm onSubmit={onSubmit} />);

    expect(screen.getByLabelText("Epochs")).toHaveAttribute("placeholder", "5");
    expect(screen.getByLabelText("Priority")).toHaveAttribute("placeholder", "1");
    expect(screen.getByLabelText("Max retries")).toHaveAttribute("placeholder", "0");

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "demo" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit job" }));

    expect(onSubmit).toHaveBeenCalledWith({
      name: "demo",
      epochs: 5,
      priority: 1,
      maxRetries: 0,
    });
  });

  it("submits typed numeric values when provided", () => {
    const onSubmit = vi.fn();
    render(<SubmitForm onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "custom" } });
    fireEvent.change(screen.getByLabelText("Epochs"), { target: { value: "12" } });
    fireEvent.change(screen.getByLabelText("Priority"), { target: { value: "9" } });
    fireEvent.change(screen.getByLabelText("Max retries"), { target: { value: "2" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit job" }));

    expect(onSubmit).toHaveBeenCalledWith({
      name: "custom",
      epochs: 12,
      priority: 9,
      maxRetries: 2,
    });
  });
});
