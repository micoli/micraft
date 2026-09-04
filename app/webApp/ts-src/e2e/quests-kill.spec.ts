/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";
import { getApiAdminNpcs } from "../generated/api/requests";

// KILL quest progress advances when the player lands the killing blow on a matching NPC.
// Accept first_steps (kill goats), spawn one, slash it down, assert the counter ticked.
test("a KILL quest counter advances when the player kills a matching NPC", async ({ page }, info) => {
  test.setTimeout(process.env.CI ? 180_000 : 90_000);
  const acct = accountFor(info);
  await connectClient(page, acct);

  // A god-mode attacker is short-circuited in NpcManager.applyDamage before the kill/quest hook,
  // so real combat needs god off; buff HP so the retaliating goat can't drop the level-1 warrior.
  const runAndSettle = async (cmd: string) => {
    const n = (await e2e(page)).notifications.length;
    await actions(page).runCommand(cmd);
    await page.waitForFunction((b) => (window.mcE2E?.notifications ?? []).length > b, n, {
      timeout: 10_000,
      polling: 100,
    });
  };
  await runAndSettle("/god:off");
  await runAndSettle("/buff hp");

  const QUEST = "first_steps.yaml";
  await runAndSettle(`/quest accept ${QUEST}`);
  await page.waitForFunction((q) => window.mcE2E?.quests?.[q]?.status === "IN_PROGRESS", QUEST, {
    timeout: 10_000,
    polling: 100,
  });

  await runAndSettle("/spawn goat 0 65 2");
  let goatId: string | undefined;
  await expect
    .poll(
      async () => {
        goatId = ((await getApiAdminNpcs(adminWorldContext(acct))).data ?? []).find((n) =>
          n.name.startsWith("Goat #"),
        )?.id;
        return goatId ?? null;
      },
      { timeout: 15_000, intervals: [500] },
    )
    .not.toBeNull();

  await actions(page).setLook(0, 0);
  await actions(page).moveForward(400);
  await page.keyboard.press("Tab");
  await page.waitForFunction(
    (id) => window.mcE2E?.combatTarget?.targetId === id,
    goatId,
    { timeout: 10_000, polling: 100 },
  );

  await expect
    .poll(
      async () => {
        await actions(page).attack("slash");
        await actions(page).moveForward(200); // stay on the goat if it bolts
        await page.waitForTimeout(850); // slash cooldown
        return (await e2e(page)).quests[QUEST]?.progress?.goat ?? 0;
      },
      { timeout: 45_000, intervals: [200] },
    )
    .toBeGreaterThanOrEqual(1);
});
