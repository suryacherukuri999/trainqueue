import { useState } from "react";
import type { CreateJobRequest } from "../types";

interface Props {
  onSubmit: (req: CreateJobRequest) => void;
}

export function SubmitForm({ onSubmit }: Props) {
  const [name, setName] = useState("");
  const [epochs, setEpochs] = useState(5);
  const [priority, setPriority] = useState(1);
  const [failAtEpoch, setFailAtEpoch] = useState("");

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    const req: CreateJobRequest = { name: name.trim(), epochs, priority };
    if (failAtEpoch !== "") req.failAtEpoch = Number(failAtEpoch);
    onSubmit(req);
    setName("");
    setFailAtEpoch("");
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
          value={epochs}
          onChange={(e) => setEpochs(Number(e.target.value))}
        />
      </label>
      <label>
        Priority
        <input
          type="number"
          min={0}
          value={priority}
          onChange={(e) => setPriority(Number(e.target.value))}
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
      <button type="submit">Submit job</button>
    </form>
  );
}
