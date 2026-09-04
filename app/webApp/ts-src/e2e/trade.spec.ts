/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions, waitForInventoryContains } from "./helpers/game";
import { postApiAdminPlayersByNameGive } from "../generated/api/requests";

// P2P trade: mutual offers + mutual accept commits an atomic swap between two nearby players.
test("two players exchange items through a mutually accepted trade (command)", async ({ browser }, info) => {
  test.setTimeout(process.env.CI ? 240_000 : 60_000);

  const a = accountFor(info, "a");
  const b = { ...accountFor(info, "b"), session: a.session };
  const contexts = await Promise.all([browser.newContext(), browser.newContext()]);
  const [pageA, pageB] = await Promise.all(contexts.map((ctx) => ctx.newPage()));

  const opts = { noWorld: true, recenter: false, timeoutScale: process.env.CI ? 2 : 1 } as const;
  await connectClient(pageA, a, opts);
  await connectClient(pageB, b, opts);

  const give = (acct: typeof a, name: string, count: number) =>
    postApiAdminPlayersByNameGive({
      ...adminWorldContext(acct),
      path: { name: acct.charName },
      body: { name, count },
    });
  await give(a, "COBBLESTONE", 2);
  await give(b, "DIRT", 3);
  await waitForInventoryContains(pageA, "COBBLESTONE", 2);
  await waitForInventoryContains(pageB, "DIRT", 3);

  // Guarantee the two are within trade range.
  await actions(pageA).runCommand("/teleport 0 70 0");
  await actions(pageB).runCommand("/teleport 0 70 0");
  await pageA.waitForTimeout(1000);

  await actions(pageA).runCommand(`/trade ${b.charName}`);
  for (const page of [pageA, pageB]) {
    await page.waitForFunction(() => window.mcE2E?.trade != null, undefined, { timeout: 15_000, polling: 200 });
  }
  const tradeId = (await e2e(pageA)).trade!.tradeId;

  await actions(pageA).runCommand(`/tradeoffer ${tradeId} {"COBBLESTONE":2}`);
  await pageB.waitForFunction(() => (window.mcE2E?.trade?.theirOffer?.COBBLESTONE ?? 0) === 2, undefined, {
    timeout: 10_000,
    polling: 200,
  });
  await actions(pageB).runCommand(`/tradeoffer ${tradeId} {"DIRT":3}`);
  await pageA.waitForFunction(() => (window.mcE2E?.trade?.theirOffer?.DIRT ?? 0) === 3, undefined, {
    timeout: 10_000,
    polling: 200,
  });

  await actions(pageA).runCommand(`/tradeaccept ${tradeId}`);
  await pageA.waitForFunction(() => window.mcE2E?.trade?.myAccepted === true, undefined, {
    timeout: 10_000,
    polling: 200,
  });
  await actions(pageB).runCommand(`/tradeaccept ${tradeId}`);

  // Swap committed: offered stacks moved, trade closed.
  await waitForInventoryContains(pageA, "DIRT", 3);
  await waitForInventoryContains(pageB, "COBBLESTONE", 2);
  expect((await e2e(pageA)).inventory.COBBLESTONE ?? 0, "A gave away the cobblestone").toBe(0);
  expect((await e2e(pageB)).inventory.DIRT ?? 0, "B gave away the dirt").toBe(0);

  await Promise.all(contexts.map((ctx) => ctx.close()));
});
