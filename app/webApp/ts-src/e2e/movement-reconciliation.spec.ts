import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";

test("client prediction converges to the server position after moving", async ({ page }, info) => {
  const acct = accountFor(info.parallelIndex);
  await connectClient(page, acct);

  const start = (await e2e(page)).position;

  await page.evaluate(() => window.mcE2E!.actions!.setLook(0, 0)); // face +Z
  await page.evaluate(() => window.mcE2E!.actions!.moveForward(1200));

  await page.waitForFunction(
    () => {
      const e = window.mcE2E!;
      return !e.hasPrediction || (e.reconcile.xz < 0.05 && e.reconcile.y < 0.05);
    },
    { timeout: 15_000, polling: 100 },
  );

  const s = await e2e(page);
  const dz = s.position.z - start.z;
  expect(dz).toBeGreaterThan(2);
  expect(dz).toBeLessThan(9);

  const dxz = Math.hypot(s.position.x - s.serverPosition.x, s.position.z - s.serverPosition.z);
  expect(dxz).toBeLessThan(0.1);
  expect(Math.abs(s.position.y - s.serverPosition.y)).toBeLessThan(0.1);
});
