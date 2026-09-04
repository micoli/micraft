/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";
import { getApiAdminNpcs } from "../generated/api/requests";

// Spawn a cat (5 HP), target it with Tab, hit it with the WARRIOR basic attack (R) until it dies.
test("a player targets and kills a spawned NPC", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  const npcs = async () => (await getApiAdminNpcs(adminWorldContext(acct))).data ?? [];
  // `/spawn` names its NPC "Cat #<hex>" — distinct from any wild cat the ecology spawned.
  const spawnedCat = async () => (await npcs()).find((n) => n.name.startsWith("Cat #"));

  await actions(page).runCommand("/spawn cat 0 65 2");
  await expect.poll(async () => (await spawnedCat()) != null, { timeout: 15_000, intervals: [500] }).toBe(true);
  const catId = (await spawnedCat())!.id;

  // Close to melee range and lock the target (nearest = the cat 2.5 blocks ahead).
  await actions(page).setLook(0, 0);
  await actions(page).moveForward(500);
  await page.keyboard.press("Tab");
  await page.waitForFunction(() => window.mcE2E?.combatTarget?.targetId != null, undefined, {
    timeout: 10_000,
    polling: 100,
  });

  await expect
    .poll(
      async () => {
        await actions(page).attack("slash");
        await page.waitForTimeout(900); // slash cooldown
        const cat = (await npcs()).find((n) => n.id === catId);
        return cat == null || cat.isDead || cat.currentHp <= 0;
      },
      { timeout: 30_000, intervals: [200] },
    )
    .toBe(true);

  expect((await e2e(page)).playerDowned, "the player never went down (god mode)").toBe(false);
});
