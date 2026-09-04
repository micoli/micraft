/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions, waitForInventoryContains } from "./helpers/game";
import { postApiAdminPlayersByNameGive } from "../generated/api/requests";

// DIRT_PILE recipe (resources/config/recipes.yaml): GRAVEL*2 + SAND*1 -> DIRT x2.
// Must be learned first; /docraft consumes the ingredients and grants the result.
test("a recipe must be learned, then /docraft consumes ingredients and grants the result", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  const give = (name: string, count: number) =>
    postApiAdminPlayersByNameGive({
      ...adminWorldContext(acct),
      path: { name: acct.charName },
      body: { name, count },
    });
  await give("GRAVEL", 2);
  await give("SAND", 1);
  await waitForInventoryContains(page, "GRAVEL", 2);
  await waitForInventoryContains(page, "SAND", 1);

  const runAndSettle = async (cmd: string) => {
    const before = (await e2e(page)).notifications.length;
    await actions(page).runCommand(cmd);
    await page.waitForFunction((n) => (window.mcE2E?.notifications ?? []).length > n, before, {
      timeout: 10_000,
      polling: 100,
    });
  };

  // Not known yet: crafting is refused, ingredients untouched.
  await runAndSettle("/docraft dirt_pile");
  expect((await e2e(page)).inventory.DIRT ?? 0, "no DIRT before learning").toBe(0);

  await runAndSettle("/learnrecipe dirt_pile");
  await runAndSettle("/docraft dirt_pile");

  await waitForInventoryContains(page, "DIRT", 2);
  const inv = (await e2e(page)).inventory;
  expect(inv.GRAVEL ?? 0, "GRAVEL consumed").toBe(0);
  expect(inv.SAND ?? 0, "SAND consumed").toBe(0);
});
