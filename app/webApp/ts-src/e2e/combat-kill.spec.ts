/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";
import { getApiAdminNpcs } from "../generated/api/requests";

// Spawn a goat (10 HP), Tab-target it and slash until it is dead.
test("a player targets and kills a spawned NPC", async ({ page }, info) => {
  test.setTimeout(process.env.CI ? 240_000 : 150_000);
  const acct = accountFor(info);
  await connectClient(page, acct);

  // NpcManager.applyDamage short-circuits a god-mode attacker before the kill hook; buff HP so the
  // goat's counterattack can't drop the level-1 warrior.
  const runAndSettle = async (cmd: string) => {
    const n = (await e2e(page)).notifications.length;
    await actions(page).runCommand(cmd);
    await page.waitForFunction((b) => (window.mcE2E?.notifications ?? []).length > b, n, { timeout: 10_000, polling: 100 });
  };
  await runAndSettle("/god:off");
  await runAndSettle("/buff hp");

  const npcs = async () => (await getApiAdminNpcs(adminWorldContext(acct))).data ?? [];
  const spawnedGoat = async () => (await npcs()).find((n) => n.name.startsWith("Goat #"));

  await runAndSettle("/spawn goat 0 65 2");
  await expect.poll(async () => (await spawnedGoat()) != null, { timeout: 15_000, intervals: [500] }).toBe(true);
  const goatId = (await spawnedGoat())!.id;

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
        await actions(page).moveForward(200);
        await page.waitForTimeout(850); // slash cooldown
        const npc = (await npcs()).find((n) => n.id === goatId);
        return npc == null || npc.isDead;
      },
      { timeout: 70_000, intervals: [200] },
    )
    .toBe(true);

  expect((await e2e(page)).playerDowned, "the player survived the fight").toBe(false);
});
