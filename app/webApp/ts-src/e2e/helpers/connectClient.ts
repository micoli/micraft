import { Page, expect } from "@playwright/test";
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
export async function connectClient(page: Page, acct: E2eAccount, lang = "en", recenter = true): Promise<void> {
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
    ([a, l]) => {
      const w = window as unknown as { __mcE2E?: boolean; __mcE2ESession?: string };
      w.__mcE2E = true;
      w.__mcE2ESession = a.session;
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
    [acct, lang] as const,
  );

  await page.goto(`/game/${encodeURIComponent(acct.email)}/${acct.playerId ?? acct.charId}`);

  try {
    await page.waitForFunction(() => (window as { mcE2E?: { ready?: boolean } }).mcE2E?.ready === true, {
      timeout: 35_000,
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

  // Wait for chunk streaming to settle: the loaded-chunk set stops growing AND every loaded chunk
  // has been meshed by the worker (mcState.chunks). Held a few polls so a mid-stream lull doesn't
  // pass.
  await page.waitForFunction(
    () => {
      const w = window as unknown as {
        mcE2E?: {
          loadedChunks?: { cx: number; cz: number }[];
          meshedChunks?: { cx: number; cz: number }[];
        };
        __chunkCount?: number;
        __meshStable?: number;
      };
      const e = w.mcE2E;
      const loaded = e?.loadedChunks ?? [];
      const meshed = new Set((e?.meshedChunks ?? []).map((c) => `${c.cx},${c.cz}`));
      const allMeshed = loaded.length > 0 && loaded.every((c) => meshed.has(`${c.cx},${c.cz}`));
      const steady = allMeshed && loaded.length === w.__chunkCount;
      w.__chunkCount = loaded.length;
      w.__meshStable = steady ? (w.__meshStable ?? 0) + 1 : 0;
      return (w.__meshStable ?? 0) >= 4;
    },
    undefined,
    { timeout: 25_000, polling: 250 },
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
    { timeout: 30_000, polling: 200 },
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

export function accountFor(index: number, slot: "a" | "b" = "a"): E2eAccount {
  return {
    email: `e2e-${slot}-${index}@test.local`,
    charName: `E2E_${slot.toUpperCase()}_${index}`,
    charId: `00000000-0000-4000-8000-${slot}${String(index).padStart(11, "0")}`,
    session: `w${index}`,
  };
}

export { expect };
