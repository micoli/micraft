import { Page, TestInfo, expect } from "@playwright/test";
import { createHash } from "node:crypto";
import { adminWorldContext, createPlayer } from "./admin";
import { CENTER_X, CENTER_Z, DROP_Y, GROUND_Y, SETTLED_Y } from "./constants";
import { actions } from "./game";
import type { E2eSnapshot } from "../../game/lib/e2eBridge";
import { putApiAdminGametime } from "../../generated/api/requests";

export interface E2eAccount {
  email: string;
  charName: string;
  charId: string;
  /** ?gameSession= — same value for every client that must share a world. */
  session: string;
  /** Filled by connectClient: the server-side player id, reserved via POST /api/admin/players. */
  playerId?: string;
}

/**
 * Seed the storage the reconnect path in GameScreen.tsx reads, mark the page as e2e, then load
 * `/game/<email>/<playerId>` and wait until the wasm client reports a settled spawn.
 *
 * auth.provider is `none` (repo default): no real token, `onConnect` skips the token check. The
 * RPG player is created server-side first via `POST /api/admin/players` (reserves the id + a
 * WARRIOR character `onConnect` then uses), so `acct.playerId` matches `mcE2E.playerId`, the
 * client gets CharacterSync, and it never routes to `/char-rpg-create`.
 */
export interface ConnectOptions {
  lang?: string;
  recenter?: boolean;
  /** Multiplies every internal wait. Multi-client specs boot several Babylon contexts at once —
   *  under CI's software GL the later clients need more slack. */
  timeoutScale?: number;
  /** Skip the chunk-mesh + gravity-settle waits (and any re-centre). The server still streams the
   *  full view radius — use `noWorld` to also switch that off. */
  worldReady?: boolean;
  /** Strongest opt-out: tells the server not to stream the world at all
   *  (`ClientMessage.Connect.needsWorld = false`) — only the spawn chunk is sent and the player is
   *  forced into flying. Implies `worldReady: false` (no chunk-mesh / gravity waits, no loading
   *  overlay). For specs that only drive UI / server state and never touch terrain or position. */
  noWorld?: boolean;
}

export async function connectClient(
  page: Page,
  acct: E2eAccount,
  opts: ConnectOptions | string = {},
  recenterLegacy = true,
): Promise<void> {
  const {
    lang = "en",
    recenter = true,
    timeoutScale = 1,
    noWorld = false,
    worldReady = !noWorld,
  } = typeof opts === "string" ? { lang: opts, recenter: recenterLegacy } : opts;
  const scale = (ms: number) => Math.round(ms * timeoutScale);
  const created = await createPlayer(acct);
  await putApiAdminGametime({ ...adminWorldContext(acct), body: { hour: 9, minute: 0 } });
  acct.playerId = created.playerId;
  const logs: string[] = [];
  page.on("console", (m) => logs.push(`[${m.type()}] ${m.text()}`));
  page.on("pageerror", (e) => logs.push(`[pageerror] ${e.message}`));
  page.on("response", (r) => {
    if (r.status() >= 400) logs.push(`[http ${r.status()}] ${r.url()}`);
  });
  page.on("requestfailed", (r) => logs.push(`[reqfail] ${r.url()} ${r.failure()?.errorText ?? ""}`));

  await page.addInitScript(
    ([a, l, noWorld]) => {
      const w = window as unknown as {
        __mcE2E?: boolean;
        __mcE2ESession?: string;
        __mcE2ENoWorld?: boolean;
      };
      w.__mcE2E = true;
      w.__mcE2ESession = a.session;
      w.__mcE2ENoWorld = noWorld;
      localStorage.setItem("micraft_last_user", a.email);
      localStorage.setItem("micraft_account_email", a.email);
      localStorage.setItem("micraft_last_lang", l);
      localStorage.setItem("micraft_last_player_" + a.email, a.charName);
      localStorage.setItem(
        "micraft_users",
        JSON.stringify({ [a.email]: [{ name: a.charName, id: a.playerId ?? a.charId }] }),
      );
      sessionStorage.setItem("micraft_auth_token", "");
    },
    [acct, lang, noWorld] as const,
  );

  await page.goto(`/game/${encodeURIComponent(acct.email)}/${acct.playerId ?? acct.charId}`);

  try {
    await page.waitForFunction(() => (window as { mcE2E?: { ready?: boolean } }).mcE2E?.ready === true, {
      timeout: scale(35_000),
    });
    await actions(page).runCommand("/god:on");
  } catch (err) {
    const diag = await page
      .evaluate(() => {
        const w = window as unknown as Record<string, unknown>;
        return {
          __mcE2E: w.__mcE2E,
          __mcE2ESession: w.__mcE2ESession,
          hasMc: typeof w.mc,
          hasUpdateE2E: typeof (w.mc as Record<string, unknown> | undefined)?.updateE2E,
          mcE2E: w.mcE2E ?? null,
          loginResult: (w.mc as { consumeLoginResult?: () => string } | undefined)?.consumeLoginResult?.(),
          mcStatePlayerId: (w.mcState as Record<string, unknown> | undefined)?.playerId,
        };
      })
      .catch((e) => ({ evalFailed: String(e) }));
    process.stderr.write(`connectClient timeout — diagnostics:\n${JSON.stringify(diag, null, 2)}\n`);
    process.stderr.write(`browser console (last 80):\n${logs.slice(-80).join("\n")}\n`);
    throw err;
  }

  if (!worldReady) {
    expect(page.url(), "client stayed in-game, no rpg-create bounce").not.toContain("char-rpg-create");
    return;
  }

  // Wait until the chunk under the player and its four orthogonal neighbours are meshed — the
  // deterministic minimum every spec relies on (a target block to break, ground to stand on,
  // remote players in view). Meshing the *whole* view distance takes far longer under CI's
  // software GL and isn't needed; specs that care assert their own wider region afterwards.
  await page.waitForFunction(
    () => {
      const e = window.mcE2E;
      if (!e?.position) return false;
      const pcx = Math.floor(e.position.x / 16);
      const pcz = Math.floor(e.position.z / 16);
      const meshed = new Set((e.meshedChunks ?? []).map((c) => `${c.cx},${c.cz}`));
      return [
        [0, 0],
        [1, 0],
        [-1, 0],
        [0, 1],
        [0, -1],
      ].every(([dx, dz]) => meshed.has(`${pcx + dx},${pcz + dz}`));
    },
    undefined,
    { timeout: scale(25_000), polling: 200 },
  );

  // God mode + re-centre: every spec (except login-spawn) runs from the middle of the map, a few
  // blocks above the terrain, then lets gravity settle it — a common, deterministic start pose.
  if (recenter) {
    await actions(page).runCommand(`/teleport ${CENTER_X} ${DROP_Y} ${CENTER_Z}`);
    await page.waitForTimeout(3000);
  }

  // Wait for the fall (from the spawn point, or from the re-centre drop) to finish: feet must reach
  // the settled height AND hold steady for a few ticks (client and server agree).
  await page.waitForFunction(
    ([groundY, settledY]) => {
      const w = window as unknown as {
        mcE2E?: { position: { y: number }; serverPosition: { y: number }; hasPrediction: boolean };
        __lastY?: number;
        __stableTicks?: number;
      };
      const e = w.mcE2E;
      if (!e || !e.hasPrediction) return false;
      const y = e.position.y;
      const landed =
        y >= groundY && y <= settledY + 1 && e.serverPosition.y >= groundY && e.serverPosition.y <= settledY + 1;
      const stable = w.__lastY !== undefined && Math.abs(y - w.__lastY) < 0.02;
      w.__lastY = y;
      w.__stableTicks = landed && stable ? (w.__stableTicks ?? 0) + 1 : 0;
      return (w.__stableTicks ?? 0) >= 5;
    },
    [GROUND_Y, SETTLED_Y] as const,
    { timeout: scale(30_000), polling: 200 },
  );

  expect(page.url(), "client stayed in-game, no rpg-create bounce").not.toContain("char-rpg-create");
}

/** Read `window.mcE2E` from the page, waiting briefly if the snapshot is between frames. */
export async function e2e(page: Page): Promise<E2eSnapshot> {
  await page.waitForFunction(() => (window as { mcE2E?: { ready?: boolean } }).mcE2E?.ready === true, {
    timeout: 10_000,
  });
  return page.evaluate(() => (window as { mcE2E?: unknown }).mcE2E as E2eSnapshot);
}

export type { E2eSnapshot };

/**
 * A fresh account + isolated GameWorld per test. Keyed by `testInfo.testId` (+ `retry`, so a
 * retried run never inherits the failed attempt's world). `parallelIndex` stays in the visible
 * names to keep server logs readable; `slot` distinguishes the clients of a multi-client spec.
 */
export function accountFor(testInfo: TestInfo, slot: "a" | "b" | "c" = "a"): E2eAccount {
  const h = createHash("sha256").update(`${testInfo.testId}:${testInfo.retry}:${slot}`).digest("hex");
  const short = h.slice(0, 12);
  return {
    email: `e2e-${slot}-${testInfo.parallelIndex}-${short}@test.local`,
    charName: `E2E_${slot.toUpperCase()}_${short}`,
    charId: `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`,
    session: `w-${short}`,
  };
}

export { expect };
