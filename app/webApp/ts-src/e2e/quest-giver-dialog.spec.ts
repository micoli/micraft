/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";
import { getApiAdminNpcs } from "../generated/api/requests";

// The leveling loop's new piece, player-driven end to end: interact with the hermit_man
// quest-giver NPC (Tab to target, X to interact — the real in-game flow, no slash-command
// shortcut), accept the offered quest through the NpcQuestDialog popup by clicking its button,
// then confirm a real kill ticks the quest's progress — same depth as the existing
// quests-kill.spec.ts ("first_steps"), which only asserts the counter advances, not full
// completion (a fleeing PASSIVE animal makes repeated spawn/retarget cycles too flaky to chain).
test("a player accepts a quest from the quest-giver dialog and its progress advances", async ({ page }, info) => {
  test.setTimeout(process.env.CI ? 180_000 : 90_000);
  const acct = accountFor(info);
  await connectClient(page, acct);

  const runAndSettle = async (cmd: string) => {
    const n = (await e2e(page)).notifications.length;
    await actions(page).runCommand(cmd);
    await page.waitForFunction((b) => (window.mcE2E?.notifications ?? []).length > b, n, {
      timeout: 10_000,
      polling: 100,
    });
  };
  // A god-mode attacker is short-circuited in NpcManager.applyDamage before the kill/quest hook.
  await runAndSettle("/god:off");
  await runAndSettle("/buff hp");

  const QUEST = "zone_tier1_foxes.yaml";

  // QuestGiverSpawner already auto-spawns one hermit_man per zone cell near the player — several
  // may be alive in neighboring cells. Spawn one more at a known spot and pick it by proximity to
  // that spot (all of them share the literal name "hermit_man", so type alone can't disambiguate).
  await runAndSettle("/spawn hermit_man 0 65 2");
  let hermitId: string | undefined;
  await expect
    .poll(
      async () => {
        const npcs = (await getApiAdminNpcs(adminWorldContext(acct))).data ?? [];
        hermitId = npcs
          .filter((n) => n.type === "hermit_man")
          .find((n) => Math.abs(n.x - 0) < 5 && Math.abs(n.z - 2) < 5)?.id;
        return hermitId ?? null;
      },
      { timeout: 15_000, intervals: [500] },
    )
    .not.toBeNull();

  await actions(page).setLook(0, 0);
  await actions(page).moveForward(400);
  await page.keyboard.press("Tab");
  await page.waitForFunction((id) => window.mcE2E?.combatTarget?.targetId === id, hermitId, {
    timeout: 10_000,
    polling: 100,
  });

  // npc_interact — real in-game interaction key, not a slash command.
  await page.keyboard.press("KeyX");

  await page.getByText("Fox Trouble").waitFor({ state: "visible", timeout: 10_000 });
  await page.getByRole("button", { name: "Accepter" }).click();

  await page.waitForFunction((q) => window.mcE2E?.quests?.[q]?.status === "IN_PROGRESS", QUEST, {
    timeout: 10_000,
    polling: 100,
  });

  // Reset to a known spot before spawning — interacting with the quest-giver (click, key
  // presses) can leave the player slightly off from where it's safe to assume the fox lands.
  await runAndSettle("/teleport 0 70 0");
  await page.waitForTimeout(1_500); // let gravity settle it back on the ground
  await runAndSettle("/spawn fox 0 65 2");
  const npcs = async () => (await getApiAdminNpcs(adminWorldContext(acct))).data ?? [];
  let foxId: string | undefined;
  await expect
    .poll(
      async () => {
        foxId = (await npcs()).find((n) => n.type === "fox" && !n.isDead)?.id;
        return foxId ?? null;
      },
      { timeout: 15_000, intervals: [500] },
    )
    .not.toBeNull();

  await actions(page).setLook(0, 0);
  await actions(page).moveForward(400);
  await page.keyboard.press("Tab");
  await page.waitForFunction((id) => window.mcE2E?.combatTarget?.targetId === id, foxId, {
    timeout: 10_000,
    polling: 100,
  });

  await expect
    .poll(
      async () => {
        await actions(page).attack("slash");
        await actions(page).moveForward(200); // stay on the fox if it flees
        await page.waitForTimeout(850); // slash cooldown
        return (await e2e(page)).quests[QUEST]?.progress?.fox ?? 0;
      },
      { timeout: 45_000, intervals: [200] },
    )
    .toBeGreaterThanOrEqual(1);
});
