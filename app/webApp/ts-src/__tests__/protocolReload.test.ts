import { describe, expect, it, vi } from "vitest";
import { reloadForProtocolMismatch } from "../lib/protocolReload";

function memoryStorage() {
  const data = new Map<string, string>();
  return { getItem: (k: string) => data.get(k) ?? null, setItem: (k: string, v: string) => void data.set(k, v) };
}

describe("reloadForProtocolMismatch", () => {
  it("reloads once, then refuses to loop within the minute", () => {
    const storage = memoryStorage();
    const reload = vi.fn();

    expect(reloadForProtocolMismatch(storage, reload, 1_000)).toBe(true);
    expect(reloadForProtocolMismatch(storage, reload, 30_000)).toBe(false);

    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("reloads again once the minute has passed", () => {
    const storage = memoryStorage();
    const reload = vi.fn();

    reloadForProtocolMismatch(storage, reload, 1_000);
    expect(reloadForProtocolMismatch(storage, reload, 61_000)).toBe(true);

    expect(reload).toHaveBeenCalledTimes(2);
  });
});
