/// <reference path="../global.d.ts" />
// Prerequisite: `make build-minigames` must have been run (and the server restarted / `make
// build`, which chains it) so that GET /api/minigames returns the `tictactoe` entry and
// /minigames/tictactoe/bundle.js is actually served — otherwise MiniGameContainer's dynamic
// import() never resolves and this spec times out. This test also depends on the server-side
// MiniGameManager/MiniGameCommand (owned separately, see CLAUDE.md "Mini-jeux") actually
// routing `/minigame ...` — best-effort: the framework + tictactoe module themselves were the
// priority for this change, not getting this spec green end to end.
import { test } from "@playwright/test";
import { Page } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";

async function playTicTacToeCell(page: Page, cellIndex: number) {
  await page.evaluate(
    (i) =>
      (window.mcE2E?.actions as unknown as { playTicTacToeCell?: (cellIndex: number) => void })?.playTicTacToeCell?.(i),
    cellIndex,
  );
}

test("two players create a tictactoe room, invite/accept, and see each other's moves", async ({ browser }, info) => {
  test.setTimeout(process.env.CI ? 240_000 : 60_000);

  const a = accountFor(info, "a");
  const b = { ...accountFor(info, "b"), session: a.session };

  const [ctxA, ctxB] = await Promise.all([browser.newContext(), browser.newContext()]);
  const [pageA, pageB] = await Promise.all([ctxA.newPage(), ctxB.newPage()]);

  const opts = { noWorld: true, recenter: false, timeoutScale: process.env.CI ? 2 : 1 } as const;
  await connectClient(pageA, a, opts);
  await connectClient(pageB, b, opts);

  await actions(pageA).runCommand("/minigame create tictactoe");
  await pageA.waitForFunction(() => window.mcE2E?.miniGameRoom?.gameType === "tictactoe", undefined, {
    timeout: 15_000,
    polling: 200,
  });

  await actions(pageA).runCommand(`/minigame invite ${b.charName}`);

  await expect
    .poll(
      async () => {
        await actions(pageB).runCommand("/minigame accept");
        return (await e2e(pageB)).miniGameRoom != null;
      },
      { timeout: 15_000, intervals: [500] },
    )
    .toBe(true);

  // Server-side truth: both clients see the same 2-member room.
  await pageA.waitForFunction(() => (window.mcE2E?.miniGameRoom?.members.length ?? 0) === 2, undefined, {
    timeout: 15_000,
    polling: 200,
  });
  await pageB.waitForFunction(() => (window.mcE2E?.miniGameRoom?.members.length ?? 0) === 2, undefined, {
    timeout: 15_000,
    polling: 200,
  });

  // A is the host (first member) -> plays X, first move.
  await playTicTacToeCell(pageA, 0);

  // B sees A's move broadcast via MiniGameAction -> local board updates.
  await pageB.waitForFunction(
    () => {
      const last = window.mcE2E?.miniGameLastAction;
      if (!last) return false;
      try {
        return JSON.parse(last.payload).cellIndex === 0;
      } catch {
        return false;
      }
    },
    undefined,
    { timeout: 15_000, polling: 200 },
  );

  await playTicTacToeCell(pageB, 1);

  await pageA.waitForFunction(
    () => {
      const last = window.mcE2E?.miniGameLastAction;
      if (!last) return false;
      try {
        return JSON.parse(last.payload).cellIndex === 1;
      } catch {
        return false;
      }
    },
    undefined,
    { timeout: 15_000, polling: 200 },
  );

  await actions(pageA).runCommand("/minigame leave");

  await Promise.all([ctxA.close(), ctxB.close()]);
});
