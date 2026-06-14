import {
  SubscriptionRegistry,
  channelFor,
  jobIdFrom,
  type RedisSub,
  type Socket,
} from "./subscriptionRegistry";

function fakeRedis() {
  return {
    subscribe: jest.fn<void, [string]>(),
    unsubscribe: jest.fn<void, [string]>(),
  } satisfies RedisSub & {
    subscribe: jest.Mock;
    unsubscribe: jest.Mock;
  };
}

function fakeSocket(readyState = 1): Socket & { send: jest.Mock } {
  return { readyState, send: jest.fn() };
}

describe("channel helpers", () => {
  it("round-trips a job id", () => {
    expect(jobIdFrom(channelFor("abc-123"))).toBe("abc-123");
  });
});

describe("SubscriptionRegistry", () => {
  it("subscribes to Redis once per job regardless of socket count", () => {
    const redis = fakeRedis();
    const reg = new SubscriptionRegistry(redis);

    reg.subscribe("job1", fakeSocket());
    reg.subscribe("job1", fakeSocket());

    expect(redis.subscribe).toHaveBeenCalledTimes(1);
    expect(redis.subscribe).toHaveBeenCalledWith(channelFor("job1"));
  });

  it("fans a message out to every open socket and skips closed ones", () => {
    const redis = fakeRedis();
    const reg = new SubscriptionRegistry(redis);
    const a = fakeSocket(1);
    const b = fakeSocket(1);
    const closed = fakeSocket(3);
    reg.subscribe("job1", a);
    reg.subscribe("job1", b);
    reg.subscribe("job1", closed);

    reg.dispatch(channelFor("job1"), "hello");

    expect(a.send).toHaveBeenCalledWith("hello");
    expect(b.send).toHaveBeenCalledWith("hello");
    expect(closed.send).not.toHaveBeenCalled();
  });

  it("unsubscribes from Redis only when the last socket leaves", () => {
    const redis = fakeRedis();
    const reg = new SubscriptionRegistry(redis);
    const a = fakeSocket();
    const b = fakeSocket();
    reg.subscribe("job1", a);
    reg.subscribe("job1", b);

    reg.removeSocket(a);
    expect(redis.unsubscribe).not.toHaveBeenCalled();
    expect(reg.jobCount()).toBe(1);

    reg.removeSocket(b);
    expect(redis.unsubscribe).toHaveBeenCalledWith(channelFor("job1"));
    expect(reg.jobCount()).toBe(0);
  });

  it("does not deliver messages for an unsubscribed job", () => {
    const redis = fakeRedis();
    const reg = new SubscriptionRegistry(redis);
    const a = fakeSocket();
    reg.subscribe("job1", a);
    reg.removeSocket(a);

    reg.dispatch(channelFor("job1"), "late");
    expect(a.send).not.toHaveBeenCalled();
  });
});
