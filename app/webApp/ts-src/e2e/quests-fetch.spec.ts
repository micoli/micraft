/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { GROUND_Y } from "./helpers/constants";
import { actions, creativePlaceBlock, targetBlockIs, waitForInventoryContains } from "./helpers/game";
import { postApiAdminPlayersByNameGive } from "../generated/api/requests";

// FETCH quest progress advances ONLY on a real item pickup (not on /give). Accept gather_gravel,
// then mine a stamped GRAVEL block (drops GRAVEL @ 90%) and walk the drop in — the quest counter
// must tick up.
test("a FETCH quest counter advances when the target item is picked up", async ({ page }, info) => {
  test.setTimeout(process.env.CI ? 180_000 : 90_000);
  const acct = accountFor(info);
  await connectClient(page, acct);

  const QUEST = "gather_gravel.yaml"; // quest id = the yaml filename
  await actions(page).runCommand(`/quest accept ${QUEST}`);
  await page.waitForFunction((q) => window.mcE2E?.quests?.[q]?.status === "IN_PROGRESS", QUEST, {
    timeout: 10_000,
    polling: 100,
  });

  // Placeable GRAVEL (placing consumes an item; the pickup after breaking is what the quest counts).
  await postApiAdminPlayersByNameGive({
    ...adminWorldContext(acct),
    path: { name: acct.charName },
    body: { name: "GRAVEL", count: 8 },
  });
  await waitForInventoryContains(page, "GRAVEL", 8);

  const collected = async () => (await e2e(page)).quests[QUEST]?.progress?.GRAVEL ?? 0;

  await actions(page).setLook(0, 1.4); // look straight down at the block ahead
  await page.waitForFunction((gy) => window.mcE2E?.targetBlock?.y === gy, GROUND_Y, {
    timeout: 10_000,
    polling: 100,
  });

  // Retry the mine+collect a few times — GRAVEL only drops GRAVEL 90% of the time.
  await expect
    .poll(
      async () => {
        const t = (await e2e(page)).targetBlock;
        if (!t) return 0;
        // Break whatever is there (grass on the first pass), then stamp + break a GRAVEL block.
        await actions(page).setBreaking(true);
        await targetBlockIs(page, t, "AIR");
        await actions(page).setBreaking(false);

        creativePlaceBlock(page, t, "GRAVEL");
        await targetBlockIs(page, t, "GRAVEL");

        await actions(page).setBreaking(true);
        await targetBlockIs(page, t, "AIR");
        await actions(page).setBreaking(false);

        await actions(page).moveForward(1200);
        await page.waitForTimeout(800);
        return collected();
      },
      { timeout: 60_000, intervals: [1000] },
    )
    .toBeGreaterThanOrEqual(1);
});
