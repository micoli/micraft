import { Page, expect } from "@playwright/test";

export interface E2eAccount {
  email: string;
  charName: string;
  charId: string;
  /** ?gameSession= — same value for every client that must share a world. */
  session: string;
}

/**
 * Seed the storage the reconnect path in GameScreen.tsx reads, mark the page as e2e, then load
 * `/game/<email>/<charId>` and wait until the wasm client reports a settled spawn.
 *
 * auth.provider is `none` (repo default): no real token, `onConnect` skips the token check and the
 * chunk socket falls back to the playerId. A character does not need to exist server-side — the
 * e2e world has no persistence so every connect is a fresh spawn.
 */
export async function connectClient(page: Page, acct: E2eAccount, lang = "en"): Promise<void> {
  const logs: string[] = [];
  page.on("console", (m) => logs.push(`[${m.type()}] ${m.text()}`));
  page.on("pageerror", (e) => logs.push(`[pageerror] ${e.message}`));

  await page.addInitScript(
    ([a, l]) => {
      const w = window as unknown as { __mcE2E?: boolean; __mcE2ESession?: string };
      w.__mcE2E = true;
      w.__mcE2ESession = a.session;
      localStorage.setItem("micraft_last_user", a.email);
      localStorage.setItem("micraft_account_email", a.email);
      localStorage.setItem("micraft_last_lang", l);
      localStorage.setItem("micraft_last_player_" + a.email, a.charName);
      localStorage.setItem("micraft_users", JSON.stringify({ [a.email]: [{ name: a.charName, id: a.charId }] }));
      sessionStorage.setItem("micraft_auth_token", "");
    },
    [acct, lang] as const,
  );

  await page.goto(`/game/${encodeURIComponent(acct.email)}/${acct.charId}`);

  try {
    await page.waitForFunction(() => (window as { mcE2E?: { ready?: boolean } }).mcE2E?.ready === true, {
      timeout: 35_000,
    });
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

  // Let gravity settle before any position assertion.
  await page.waitForFunction(
    () => {
      const w = window as unknown as { mcE2E?: { position: { y: number } }; __lastY?: number };
      const y = w.mcE2E!.position.y;
      const stable = w.__lastY !== undefined && Math.abs(y - w.__lastY) < 0.01;
      w.__lastY = y;
      return stable;
    },
    { timeout: 10_000, polling: 250 },
  );
}

/** Read `window.mcE2E` from the page. */
export async function e2e(page: Page) {
  return page.evaluate(() => (window as { mcE2E?: unknown }).mcE2E as E2eSnapshot);
}

export interface E2eSnapshot {
  ready: boolean;
  playerId: string;
  playerName: string;
  position: { x: number; y: number; z: number };
  serverPosition: { x: number; y: number; z: number };
  yaw: number;
  pitch: number;
  stance: string;
  hasPrediction: boolean;
  reconcile: { xz: number; y: number };
  loadedChunks: { cx: number; cz: number }[];
  meshedChunks: { cx: number; cz: number }[];
  inventory: Record<string, number>;
  targetBlock: { x: number; y: number; z: number } | null;
  remotePlayers: { id: string; name: string; x: number; y: number; z: number }[];
  lastWorldUpdate: { x: number; y: number; z: number; block: string }[] | null;
}

export function accountFor(index: number, slot: "a" | "b" = "a"): E2eAccount {
  return {
    email: `e2e-${slot}-${index}@test.local`,
    charName: `E2E_${slot.toUpperCase()}_${index}`,
    charId: `00000000-0000-4000-8000-${slot}${String(index).padStart(11, "0")}`,
    session: `w${index}`,
  };
}

export { expect };
