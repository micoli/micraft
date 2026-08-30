import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";

test("two clients in one world see each other and each other's movement", async ({ browser }, info) => {
  const a = accountFor(info.parallelIndex, "a");
  const b = { ...accountFor(info.parallelIndex, "b"), session: a.session }; // same world

  const ctxA = await browser.newContext();
  const ctxB = await browser.newContext();
  const pageA = await ctxA.newPage();
  const pageB = await ctxB.newPage();

  await connectClient(pageA, a);
  await connectClient(pageB, b);

  const idB = (await e2e(pageB)).playerId;

  await pageA.waitForFunction((name) => window.mcE2E?.remotePlayers.some((p) => p.name === name) ?? false, b.charName, {
    timeout: 15_000,
    polling: 200,
  });
  const seenByA = (await e2e(pageA)).remotePlayers.find((p) => p.name === b.charName)!;
  expect(seenByA.id).toBe(idB);

  // Only the other real player — maxNpcs=0 and worlds are isolated.
  expect((await e2e(pageA)).remotePlayers).toHaveLength(1);

  const zBefore = seenByA.z;
  await pageB.evaluate(() => window.mcE2E!.actions!.setLook(0, 0));
  await pageB.evaluate(() => window.mcE2E!.actions!.moveForward(1000));

  await pageA.waitForFunction(
    ([name, z0]) => {
      const p = window.mcE2E?.remotePlayers.find((rp) => rp.name === name);
      return (p?.z ?? z0) - (z0 as number) > 2;
    },
    [b.charName, zBefore] as const,
    { timeout: 15_000, polling: 200 },
  );

  await ctxA.close();
  await ctxB.close();
});
