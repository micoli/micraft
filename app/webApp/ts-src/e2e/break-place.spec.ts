/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { GROUND_Y } from "./helpers/constants";

test("breaking then placing a block each propagate a WorldUpdate", async ({ page }, info) => {
  const acct = accountFor(info.parallelIndex);
  await connectClient(page, acct);

  // Look down so the crosshair lands on the ground just ahead.
  await page.evaluate(() => window.mcE2E!.actions!.setLook(0, 1.4));

  await page.waitForFunction((groundY) => window.mcE2E?.targetBlock?.y === groundY, GROUND_Y, {
    timeout: 10_000,
    polling: 100,
  });
  const target = (await e2e(page)).targetBlock!;

  await page.evaluate(() => window.mcE2E!.actions!.breakTargeted());
  await page.waitForFunction(
    (t) =>
      window.mcE2E?.lastWorldUpdate?.some(
        (c) => c.x === t.x && c.y === t.y && c.z === t.z && c.block.toLowerCase() === "air",
      ) ?? false,
    target,
    { timeout: 15_000, polling: 100 },
  );

  // Now place a block back on that cell.
  await page.evaluate((t) => window.mcState.events.push(`creative_place:${t.x},${t.y},${t.z},COBBLESTONE,0`), target);
  await page.waitForFunction(
    (t) =>
      window.mcE2E?.lastWorldUpdate?.some(
        (c) => c.x === t.x && c.y === t.y && c.z === t.z && c.block.toLowerCase() !== "air",
      ) ?? false,
    target,
    { timeout: 15_000, polling: 100 },
  );

  const s = await e2e(page);
  expect(s.lastWorldUpdate, "a WorldUpdate is recorded").not.toBeNull();
});
