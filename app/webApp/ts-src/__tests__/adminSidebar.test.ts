import { afterEach, describe, expect, it, vi } from "vitest";
import { loadSidebarCollapsed, saveSidebarCollapsed } from "../admin/sidebar";

const KEY = "micraft-admin-sidebar-collapsed";

/**
 * Minimal Storage stand-in. This test suite's jsdom has no localStorage at all — which is exactly why
 * the helpers guard every access — so the fake is installed rather than assumed.
 */
function fakeStorage(initial: Record<string, string> = {}) {
  const map = new Map(Object.entries(initial));
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => void map.set(key, value),
    removeItem: (key: string) => void map.delete(key),
    clear: () => map.clear(),
    key: () => null,
    length: 0,
    read: (key: string) => map.get(key) ?? null,
  };
}

afterEach(() => vi.unstubAllGlobals());

describe("sidebar collapse persistence", () => {
  it("defaults to expanded on a first visit", () => {
    vi.stubGlobal("localStorage", fakeStorage());
    // the labelled nav is the discoverable state, so it is the safe default
    expect(loadSidebarCollapsed()).toBe(false);
  });

  it("round-trips both states", () => {
    const storage = fakeStorage();
    vi.stubGlobal("localStorage", storage);
    saveSidebarCollapsed(true);
    expect(storage.read(KEY)).toBe("1");
    expect(loadSidebarCollapsed()).toBe(true);

    saveSidebarCollapsed(false);
    expect(storage.read(KEY)).toBe("0");
    expect(loadSidebarCollapsed()).toBe(false);
  });

  it("treats junk in storage as expanded", () => {
    vi.stubGlobal("localStorage", fakeStorage({ [KEY]: "yes please" }));
    expect(loadSidebarCollapsed()).toBe(false);
  });

  it("survives storage being unavailable", () => {
    // Safari in private mode, and this very test environment: reading must not take the page down
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("denied");
      },
      setItem: () => {
        throw new Error("denied");
      },
    });
    expect(loadSidebarCollapsed()).toBe(false);
    expect(() => saveSidebarCollapsed(true)).not.toThrow();
  });

  it("survives there being no storage object at all", () => {
    vi.stubGlobal("localStorage", undefined);
    expect(loadSidebarCollapsed()).toBe(false);
    expect(() => saveSidebarCollapsed(true)).not.toThrow();
  });
});
