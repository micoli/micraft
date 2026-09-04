/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";

// Guild lifecycle via slash commands, roster synced to both clients (like the group feature but
// persistent, with ranks). Bank is drag-drop only, out of scope here.
test("a player founds a guild and another joins it (command)", async ({ browser }, info) => {
  test.setTimeout(process.env.CI ? 240_000 : 60_000);

  const a = accountFor(info, "a");
  const b = { ...accountFor(info, "b"), session: a.session };
  const contexts = await Promise.all([browser.newContext(), browser.newContext()]);
  const [pageA, pageB] = await Promise.all(contexts.map((ctx) => ctx.newPage()));

  const opts = { noWorld: true, recenter: false, timeoutScale: process.env.CI ? 2 : 1 } as const;
  await connectClient(pageA, a, opts);
  await connectClient(pageB, b, opts);
  const idA = (await e2e(pageA)).playerId;

  await actions(pageA).runCommand("/guild create Wolves WLV");
  await pageA.waitForFunction((owner) => window.mcE2E?.guild?.ownerId === owner, idA, {
    timeout: 15_000,
    polling: 200,
  });

  await actions(pageA).runCommand(`/guild invite ${b.charName}`);
  await expect
    .poll(
      async () => {
        await actions(pageB).runCommand("/guild accept");
        return (await e2e(pageB)).guild != null;
      },
      { timeout: 15_000, intervals: [500] },
    )
    .toBe(true);

  const roster = [a.charName, b.charName].sort();
  for (const page of [pageA, pageB]) {
    await page.waitForFunction(
      (names) => {
        const g = window.mcE2E?.guild;
        return g != null && g.members.map((m) => m.playerName).sort().join() === names.join();
      },
      roster,
      { timeout: 20_000, polling: 200 },
    );
  }

  const gA = (await e2e(pageA)).guild!;
  expect(gA.name).toBe("Wolves");
  expect(gA.tag).toBe("WLV");
  const rankOf = (name: string) => gA.members.find((m) => m.playerName === name)!.rank;
  expect(rankOf(a.charName), "founder outranks the recruit").not.toBe(rankOf(b.charName));

  await Promise.all(contexts.map((ctx) => ctx.close()));
});
