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

  // A sees B, and only B (maxNpcs=0, worlds isolated).
  const seenByA = await pageA.waitForFunction(
    (name) => {
      const rp = window.mcE2E?.remotePlayers ?? [];
      const hit = rp.find((p) => p.name === name);
      return hit && rp.length === 1 ? hit : null;
    },
    b.charName,
    { timeout: 20_000, polling: 200 },
  );
  const before = await seenByA.jsonValue();
  expect(before.id).toBe(idB);

  // B walks; A sees it move.
  await pageB.evaluate(() => window.mcE2E!.actions!.setLook(0, 0));
  await pageB.evaluate(() => window.mcE2E!.actions!.moveForward(1500));

  await pageA.waitForFunction(
    ([name, z0]) => {
      const p = window.mcE2E?.remotePlayers.find((rp) => rp.name === name);
      return p !== undefined && Math.abs(p.z - (z0 as number)) > 1.5;
    },
    [b.charName, before.z] as const,
    { timeout: 20_000, polling: 200 },
  );

  await ctxA.close();
  await ctxB.close();
});
