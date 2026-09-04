/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";

// Holding the sneak / crawl keys must change the server-authoritative stance (height + speed).
test("holding sneak then crawl switches the player stance and reverts on release", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  expect((await e2e(page)).stance, "starts standing").toBe("standing");

  await page.keyboard.down("ShiftLeft");
  await page.waitForFunction(() => window.mcE2E?.stance === "sneaking", undefined, {
    timeout: 5_000,
    polling: 100,
  });
  await page.keyboard.up("ShiftLeft");
  await page.waitForFunction(() => window.mcE2E?.stance === "standing", undefined, {
    timeout: 5_000,
    polling: 100,
  });

  await page.keyboard.down("ControlLeft");
  await page.waitForFunction(() => window.mcE2E?.stance === "crawling", undefined, {
    timeout: 5_000,
    polling: 100,
  });
  await page.keyboard.up("ControlLeft");
  await page.waitForFunction(() => window.mcE2E?.stance === "standing", undefined, {
    timeout: 5_000,
    polling: 100,
  });
});
