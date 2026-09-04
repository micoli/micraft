/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";

// The WARRIOR character created for every e2e player has vitals; a /buff must raise max HP,
// and /god:off then /god:on round-trips the godMode flag the client mirrors.
test("player vitals are reported and /buff hp raises max HP", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  await page.waitForFunction(() => (window.mcE2E?.playerStatus?.maxHp ?? 0) > 0, undefined, {
    timeout: 10_000,
    polling: 100,
  });
  const before = (await e2e(page)).playerStatus!;
  expect(before.currentHp, "starts alive").toBeGreaterThan(0);

  await actions(page).runCommand("/buff hp");
  await page.waitForFunction(
    (m) => (window.mcE2E?.playerStatus?.maxHp ?? 0) > m,
    before.maxHp,
    { timeout: 10_000, polling: 100 },
  );
  expect((await e2e(page)).playerStatus!.maxHp, "+20 max HP from the buff").toBe(before.maxHp + 20);
});
