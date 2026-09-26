/// <reference path="../../global.d.ts" />
import type { CDPSession, Page } from "@playwright/test";
import type { ClientPerfSnapshot } from "../../game/lib/perf/perfCollector";
import { PERF_PORT } from "../playwright.config";
import { cameraXz } from "./navigator";

const BASE = `http://localhost:${PERF_PORT}`;

// Seeded by :server:seedPerfAdmin into the perf data root (see server/build.gradle.kts).
const PERF_ADMIN_EMAIL = "perf-admin@test.local";
const PERF_ADMIN_PASSWORD = "perf-admin-password";

export const PERF_CHARACTER = { email: "perf-runner@test.local", name: "PerfRunner" };

async function login(email: string, password: string): Promise<string> {
  const r = await fetch(`${BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!r.ok) throw new Error(`login ${email} failed: ${r.status} ${await r.text()}`);
  return ((await r.json()) as { token: string }).token;
}

let adminToken: Promise<string> | null = null;

export async function adminFetch(path: string, init: RequestInit = {}): Promise<Response> {
  adminToken ??= login(PERF_ADMIN_EMAIL, PERF_ADMIN_PASSWORD);
  return fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${await adminToken}`,
      ...(init.headers ?? {}),
    },
  });
}

/** Reserve the runner's Character (WARRIOR, admin in-game RBAC group for /teleport) and log it in. */
async function prepareCharacter(): Promise<{ playerId: string; token: string }> {
  const r = await adminFetch("/api/admin/players", {
    method: "POST",
    body: JSON.stringify({
      name: PERF_CHARACTER.name,
      email: PERF_CHARACTER.email,
      groups: ["admin"],
      characterClass: "WARRIOR",
      str: 8,
      dex: 8,
      intel: 8,
      wis: 8,
      con: 8,
      cha: 8,
    }),
  });
  if (!r.ok) throw new Error(`reserve character failed: ${r.status} ${await r.text()}`);
  const { playerId } = (await r.json()) as { playerId: string };
  return { playerId, token: await login(PERF_CHARACTER.email, "perf") };
}

/**
 * Load the game with the perf collector on. `__mcE2E` is set without a session: it installs the
 * `mcE2E.actions` bridge (slash commands) but keeps the per-tick E2E snapshot off, which would
 * otherwise be measured too.
 */
export async function enterGame(page: Page): Promise<void> {
  const { playerId, token } = await prepareCharacter();
  await page.addInitScript(
    ([email, name, id, t]) => {
      const w = window as unknown as { __mcPerf?: boolean; __mcE2E?: boolean };
      w.__mcPerf = true;
      w.__mcE2E = true;
      localStorage.setItem("micraft_last_user", email);
      localStorage.setItem("micraft_account_email", email);
      localStorage.setItem("micraft_last_lang", "en");
      localStorage.setItem("micraft_last_player_" + email, name);
      localStorage.setItem("micraft_users", JSON.stringify({ [email]: [{ name, id }] }));
      sessionStorage.setItem("micraft_auth_token", t);
    },
    [PERF_CHARACTER.email, PERF_CHARACTER.name, playerId, token] as const,
  );
  await page.goto(`/game/${encodeURIComponent(PERF_CHARACTER.email)}/${playerId}`);
  await page.waitForFunction(() => !!window.mcPerf && !!window.mcE2E?.actions, undefined, { timeout: 60_000 });
  // Falls and mobs must not end a run early.
  await runCommand(page, "/god:on");
}

export async function runCommand(page: Page, cmd: string): Promise<void> {
  await page.evaluate((c) => window.mcE2E!.actions!.runCommand(c), cmd);
}

/** Wait until the meshed chunk count has not changed for `stableMs` — streaming around the player is done. */
export async function waitForChunksSettled(page: Page, stableMs = 3_000, timeoutMs = 120_000): Promise<number> {
  const deadline = Date.now() + timeoutMs;
  let last = -1;
  let since = Date.now();
  while (Date.now() < deadline) {
    const count = await page.evaluate(() => Object.keys(window.mcState.chunks ?? {}).length);
    if (count !== last) {
      last = count;
      since = Date.now();
    } else if (count > 0 && Date.now() - since >= stableMs) {
      return count;
    }
    await page.waitForTimeout(500);
  }
  throw new Error(`chunks never settled (last count ${last})`);
}

export interface WindowResult {
  client: ClientPerfSnapshot;
  server: unknown;
  /** Horizontal camera travel over the window, in blocks — proves a traversal actually moved. */
  travelledBlocks: number;
  /** Times the traversal got stuck and had to detour (0 for scenarios that stand still). */
  stuckEvents: number;
  /** JS heap still in use after a forced full GC at the end of the window: live data, not garbage. */
  clientLiveHeapBytes: number;
  /** ArrayBuffer backing stores alive at the same moment (typed arrays, vertex data): not in the JS heap. */
  clientArrayBufferBytes: number;
}

const cdpSessions = new WeakMap<Page, Promise<CDPSession>>();

/** Forced after the window's snapshot, so the collection never lands in the measured frames. */
async function liveMemory(page: Page): Promise<{ heapBytes: number; arrayBufferBytes: number }> {
  if (!cdpSessions.has(page)) cdpSessions.set(page, page.context().newCDPSession(page));
  const cdp = await cdpSessions.get(page)!;
  await cdp.send("HeapProfiler.collectGarbage");
  const { usedSize, backingStorageSize } = await cdp.send("Runtime.getHeapUsage");
  return { heapBytes: usedSize, arrayBufferBytes: backingStorageSize };
}

/** Measure one window: reset server + client, let `during` run, snapshot both. */
export async function measure(page: Page, during: () => Promise<number | void>): Promise<WindowResult> {
  const reset = await adminFetch("/api/admin/perf/reset", { method: "POST" });
  if (reset.status !== 204) throw new Error(`perf reset failed: ${reset.status}`);
  await page.evaluate(() => window.mcPerf!.reset());
  const from = await cameraXz(page);
  const stuckEvents = (await during()) ?? 0;
  const to = await cameraXz(page);
  const client = await page.evaluate(() => window.mcPerf!.snapshot());
  const server = await (await adminFetch("/api/admin/perf/snapshot")).json();
  const live = await liveMemory(page);
  return {
    client,
    server,
    travelledBlocks: Math.hypot(to.x - from.x, to.z - from.z),
    stuckEvents,
    clientLiveHeapBytes: live.heapBytes,
    clientArrayBufferBytes: live.arrayBufferBytes,
  };
}
