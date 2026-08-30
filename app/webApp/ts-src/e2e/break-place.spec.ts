import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { GROUND_Y } from "./helpers/constants";

test("breaking a grass block propagates a WorldUpdate and drops an item", async ({ page }, info) => {
  const acct = accountFor(info.parallelIndex);
  await connectClient(page, acct);

  // Look down so the crosshair lands on the ground just ahead.
  await page.evaluate(() => window.mcE2E!.actions!.setLook(0, 1.4));

  await page.waitForFunction((groundY) => window.mcE2E?.targetBlock?.y === groundY, GROUND_Y, {
    timeout: 10_000,
    polling: 100,
  });
  const target = (await e2e(page)).targetBlock!;
  const invBefore = { ...(await e2e(page)).inventory };

  await page.evaluate(() => window.mcE2E!.actions!.breakTargeted());

  await page.waitForFunction(
    (t) =>
      window.mcE2E?.lastWorldUpdate?.some(
        (c) => c.x === t.x && c.y === t.y && c.z === t.z && c.block.toLowerCase() === "air",
      ) ?? false,
    target,
    { timeout: 10_000, polling: 100 },
  );

  await page.waitForFunction(
    (before) => {
      const inv = window.mcE2E!.inventory;
      const total = (o: Record<string, number>) => Object.values(o).reduce((a, b) => a + b, 0);
      return total(inv) > total(before);
    },
    invBefore,
    { timeout: 10_000, polling: 200 },
  );

  const s = await e2e(page);
  expect(
    Object.entries(s.inventory).some(([k, v]) => (invBefore[k] ?? 0) < v),
    "an item stack grew after the break",
  ).toBe(true);
});
