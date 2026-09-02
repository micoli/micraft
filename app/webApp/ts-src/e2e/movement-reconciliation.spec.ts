/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";

test("client prediction tracks the server position while and after moving", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  const start = (await e2e(page)).position;

  await actions(page).setLook(0, 0); // face +Z
  await actions(page).moveForward(1200);

  // Wait until the move is done and the predicted position holds steady next to the server one.
  await page.waitForFunction(
    () => {
      const w = window as unknown as { mcE2E?: NonNullable<Window["mcE2E"]>; __recZ?: number; __recN?: number };
      const e = w.mcE2E;
      if (!e) return false;
      const settled = w.__recZ !== undefined && Math.abs(e.position.z - w.__recZ) < 0.02;
      w.__recZ = e.position.z;
      w.__recN = settled ? (w.__recN ?? 0) + 1 : 0;
      return (w.__recN ?? 0) >= 5 && e.reconcile.xz < 0.6 && e.reconcile.y < 0.6;
    },
    { timeout: 15_000, polling: 150 },
  );

  const s = await e2e(page);

  // Moved forward, a plausible distance for ~1.2 s of walking.
  const dz = s.position.z - start.z;
  expect(dz).toBeGreaterThan(2);
  expect(dz).toBeLessThan(9);

  // Prediction stayed locked to the server, not drifting away.
  const dxz = Math.hypot(s.position.x - s.serverPosition.x, s.position.z - s.serverPosition.z);
  expect(dxz).toBeLessThan(0.6);
  expect(Math.abs(s.position.y - s.serverPosition.y)).toBeLessThan(0.6);
});
