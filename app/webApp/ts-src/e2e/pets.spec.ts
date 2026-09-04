/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";
import { getApiAdminNpcs } from "../generated/api/requests";

// Tame a goat (tameBaseChance 0.6) into the pet roster, then dismiss the active pet.
test("a player tames a wild NPC and can dismiss the pet", async ({ page }, info) => {
  test.setTimeout(process.env.CI ? 180_000 : 90_000);
  const acct = accountFor(info);
  await connectClient(page, acct);

  await actions(page).runCommand("/spawn goat 0 65 2");
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
  await page.waitForFunction((id) => window.mcE2E?.combatTarget?.targetId === id, goatId, {
    timeout: 10_000,
    polling: 100,
  });

  // Chance-based — retry until the goat joins the roster.
  await expect
    .poll(
      async () => {
        await actions(page).runCommand("/tame");
        await page.waitForTimeout(1200);
        return (await e2e(page)).petRoster.pets.length;
      },
      { timeout: 40_000, intervals: [500] },
    )
    .toBeGreaterThanOrEqual(1);

  // A successful tame auto-summons the pet.
  await page.waitForFunction(() => window.mcE2E?.petRoster.activePetId != null, undefined, { timeout: 10_000, polling: 100 });

  await actions(page).runCommand("/pet dismiss");
  await page.waitForFunction(() => window.mcE2E?.petRoster.pets[0]?.spawned === false, undefined, {
    timeout: 10_000,
    polling: 100,
  });
  const after = (await e2e(page)).petRoster;
  expect(after.activePetId ?? null, "no active pet after dismiss").toBeNull();
  expect(after.pets.length, "dismissed pet stays on the roster").toBeGreaterThanOrEqual(1);
});
