/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { GROUND_Y } from "./helpers/constants";
import { actions, creativePlaceBlock, targetBlockIs, targetBlockIsNot, waitForInventoryContains } from "./helpers/game";
import { postApiAdminPlayersByNameGive } from "../generated/api/requests";

test("breaking then placing a block each propagate a WorldUpdate", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  await postApiAdminPlayersByNameGive({
    ...adminWorldContext(acct),
    path: { name: acct.charName },
    body: { name: "COBBLESTONE", count: 1 },
  });
  await waitForInventoryContains(page, "COBBLESTONE", 1);

  // Look down so the crosshair lands on the ground just ahead.
  await actions(page).setLook(0, 1.4);

  await page.waitForFunction((groundY) => window.mcE2E?.targetBlock?.y === groundY, GROUND_Y, {
    timeout: 10_000,
    polling: 100,
  });
  const target = (await e2e(page)).targetBlock!;

  await actions(page).breakTargeted();
  await targetBlockIs(page, target, "AIR");
  await creativePlaceBlock(page, target, "COBBLESTONE");
  await targetBlockIsNot(page, target, "AIR");

  const s = await e2e(page);
  expect(s.lastWorldUpdate, "a WorldUpdate is recorded").not.toBeNull();
});

test("the admin API seeds a fixture into this test's isolated world", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);
  await postApiAdminPlayersByNameGive({
    ...adminWorldContext(acct),
    path: { name: acct.charName },
    body: { name: "COBBLESTONE", count: 7 },
  });
  await waitForInventoryContains(page, "COBBLESTONE", 7);
});
