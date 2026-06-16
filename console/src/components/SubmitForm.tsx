import { useState } from "react";
import type { CreateJobRequest } from "../types";

interface Props {
  onSubmit: (req: CreateJobRequest) => void;
}

const DEFAULT_EPOCHS = 5;
const DEFAULT_PRIORITY = 1;
const DEFAULT_MAX_RETRIES = 0;

export function SubmitForm({ onSubmit }: Props) {
  const [name, setName] = useState("");
  const [epochs, setEpochs] = useState("");
  const [priority, setPriority] = useState("");
  const [failAtEpoch, setFailAtEpoch] = useState("");
  const [maxRetries, setMaxRetries] = useState("");

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    const req: CreateJobRequest = {
      name: name.trim(),
      epochs: epochs === "" ? DEFAULT_EPOCHS : Number(epochs),
      priority: priority === "" ? DEFAULT_PRIORITY : Number(priority),
      maxRetries: maxRetries === "" ? DEFAULT_MAX_RETRIES : Number(maxRetries),
    };
    if (failAtEpoch !== "") req.failAtEpoch = Number(failAtEpoch);
    onSubmit(req);
    setName("");
    setEpochs("");
    setPriority("");
    setFailAtEpoch("");
    setMaxRetries("");
  }

  return (
    <form onSubmit={submit} className="submit-form">
      <label>
        Name
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <label>
        Epochs
        <input
          type="number"
          min={1}
          placeholder={String(DEFAULT_EPOCHS)}
          value={epochs}
          onChange={(e) => setEpochs(e.target.value)}
        />
      </label>
      <label>
        Priority
        <input
          type="number"
          min={0}
          placeholder={String(DEFAULT_PRIORITY)}
          value={priority}
          onChange={(e) => setPriority(e.target.value)}
        />
      </label>
      <label>
        Fail at epoch
        <input
          type="number"
          min={1}
          placeholder="optional"
          value={failAtEpoch}
          onChange={(e) => setFailAtEpoch(e.target.value)}
        />
      </label>
      <label>
        Max retries
        <input
          type="number"
          min={0}
          placeholder={String(DEFAULT_MAX_RETRIES)}
          value={maxRetries}
          onChange={(e) => setMaxRetries(e.target.value)}
        />
      </label>
      <button type="submit" className="btn-primary">Submit job</button>
    </form>
  );
}
