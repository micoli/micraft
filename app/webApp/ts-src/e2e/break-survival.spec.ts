/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { GROUND_Y } from "./helpers/constants";
import { actions, targetBlockIs, waitForInventoryContains } from "./helpers/game";

// Survival break (no creative shortcut): hold the primary button, let the server tick the
// block's hardness, then walk over the dropped item and pick it up. GRASS drops DIRT at 100%.
test("breaking a grass block in survival drops DIRT and the player picks it up", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  // Empty hotbar => no place-mode; a plain hold breaks the targeted block.
  await actions(page).setLook(0, 1.4);
  await page.waitForFunction((groundY) => window.mcE2E?.targetBlock?.y === groundY, GROUND_Y, {
    timeout: 10_000,
    polling: 100,
  });
  const target = (await e2e(page)).targetBlock!;

  await actions(page).setBreaking(true);
  await targetBlockIs(page, target, "AIR");
  await actions(page).setBreaking(false);

  // Step onto the hole so the drop is within pickup range.
  await actions(page).moveForward(1000);
  await waitForInventoryContains(page, "DIRT", 1);

  const s = await e2e(page);
  expect(s.inventory.DIRT ?? 0, "DIRT collected from the grass drop").toBeGreaterThanOrEqual(1);
});
