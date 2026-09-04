/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions, waitForInventoryContains } from "./helpers/game";
import { getApiAdminNpcs } from "../generated/api/requests";

// Spawn a seller NPC, fund the player, buy from its shop (COBBLESTONE @ 5 copper).
test("a player buys an item from a seller NPC's shop", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  await actions(page).runCommand("/spawn seller 3 65 3");

  let sellerId: string | undefined;
  await expect
    .poll(
      async () => {
        const { data } = await getApiAdminNpcs(adminWorldContext(acct));
        sellerId = (data ?? []).find((n) => n.type === "seller")?.id;
        return sellerId ?? null;
      },
      { timeout: 15_000, intervals: [500] },
    )
    .not.toBeNull();

  await actions(page).runCommand("/give:money 100");
  await page.waitForFunction(() => (window.mcE2E?.wallet ?? 0) >= 100, undefined, { timeout: 10_000, polling: 100 });

  await actions(page).runCommand(`/npcbuy ${sellerId} COBBLESTONE 2`);
  await waitForInventoryContains(page, "COBBLESTONE", 2);
  expect((await e2e(page)).wallet, "2 x COBBLESTONE @ 5 copper deducted").toBe(90);
});
