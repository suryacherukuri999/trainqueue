import http from "http";
import Redis from "ioredis";
import { WebSocketServer } from "ws";
import { SubscriptionRegistry } from "./subscriptionRegistry";

const PORT = Number(process.env.PORT ?? 8081);
const REDIS_URL = process.env.REDIS_URL ?? "redis://localhost:6379";

const sub = new Redis(REDIS_URL);
let redisReady = false;
sub.on("ready", () => {
  redisReady = true;
});
sub.on("error", (e) => {
  redisReady = false;
  console.error("redis error:", e.message);
});
sub.on("end", () => {
  redisReady = false;
});

const registry = new SubscriptionRegistry({
  subscribe: (channel) => void sub.subscribe(channel),
  unsubscribe: (channel) => void sub.unsubscribe(channel),
});

sub.on("message", (channel, message) => registry.dispatch(channel, message));

const server = http.createServer((req, res) => {
  // liveness: the process is up (don't get killed just because Redis is down)
  if (req.url === "/healthz") {
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end("ok");
    return;
  }
  // readiness: only serve traffic when Redis is connected
  if (req.url === "/readyz") {
    res.writeHead(redisReady ? 200 : 503, { "Content-Type": "text/plain" });
    res.end(redisReady ? "ready" : "redis unavailable");
    return;
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({ server, path: "/ws" });

wss.on("connection", (ws) => {
  ws.on("message", (data) => {
    try {
      const msg = JSON.parse(data.toString());
      if (typeof msg.subscribe === "string") {
        registry.subscribe(msg.subscribe, ws);
      }
    } catch {
      // ignore malformed client frames
    }
  });
  ws.on("close", () => registry.removeSocket(ws));
});

server.listen(PORT, () => console.log(`gateway listening on :${PORT} (redis ${REDIS_URL})`));

function shutdown() {
  console.log("gateway shutting down");
  wss.close();
  void sub.quit().catch(() => undefined);
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5000).unref();
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
