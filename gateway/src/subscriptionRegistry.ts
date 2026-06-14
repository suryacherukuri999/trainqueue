// Maps job ids to the sockets watching them, and keeps exactly one Redis
// subscription alive per job — opened on the first watcher, dropped on the last.

const WS_OPEN = 1;

export const channelFor = (jobId: string) => `job:${jobId}:events`;
export const jobIdFrom = (channel: string) =>
  channel.slice("job:".length, -":events".length);

export interface Socket {
  readyState: number;
  send(data: string): void;
}

export interface RedisSub {
  subscribe(channel: string): void;
  unsubscribe(channel: string): void;
}

export class SubscriptionRegistry {
  private readonly byJob = new Map<string, Set<Socket>>();

  constructor(private readonly redis: RedisSub) {}

  subscribe(jobId: string, socket: Socket): void {
    let sockets = this.byJob.get(jobId);
    if (!sockets) {
      sockets = new Set();
      this.byJob.set(jobId, sockets);
      this.redis.subscribe(channelFor(jobId));
    }
    sockets.add(socket);
  }

  removeSocket(socket: Socket): void {
    for (const [jobId, sockets] of this.byJob) {
      if (sockets.delete(socket) && sockets.size === 0) {
        this.byJob.delete(jobId);
        this.redis.unsubscribe(channelFor(jobId));
      }
    }
  }

  dispatch(channel: string, message: string): void {
    const sockets = this.byJob.get(jobIdFrom(channel));
    if (!sockets) return;
    for (const socket of sockets) {
      if (socket.readyState === WS_OPEN) {
        socket.send(message);
      }
    }
  }

  jobCount(): number {
    return this.byJob.size;
  }
}
