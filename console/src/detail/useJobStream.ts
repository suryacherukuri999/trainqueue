import { useEffect, useReducer, useState } from "react";
import { detailReducer, initialDetail, type StreamEvent } from "./detailReducer";

const WS_URL = "ws://localhost:8081/ws";
const MAX_BACKOFF_MS = 10000;

/** Subscribes to a job's live event stream over WebSocket, reconnecting with backoff. */
export function useJobStream(jobId: string) {
  const [state, dispatch] = useReducer(detailReducer, initialDetail);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    let closedByUs = false;
    let backoff = 1000;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let ws: WebSocket;

    const connect = () => {
      ws = new WebSocket(WS_URL);
      ws.onopen = () => {
        setConnected(true);
        backoff = 1000;
        ws.send(JSON.stringify({ subscribe: jobId }));
      };
      ws.onmessage = (e) => {
        try {
          dispatch(JSON.parse(e.data) as StreamEvent);
        } catch {
          // ignore malformed frames
        }
      };
      ws.onclose = () => {
        setConnected(false);
        if (!closedByUs) {
          retry = setTimeout(connect, backoff);
          backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        }
      };
      ws.onerror = () => ws.close();
    };

    connect();
    return () => {
      closedByUs = true;
      if (retry) clearTimeout(retry);
      ws.close();
    };
  }, [jobId]);

  return { state, connected };
}
