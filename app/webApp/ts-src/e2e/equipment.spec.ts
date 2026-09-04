/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { admin } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";

type CharSync = { derived: { maxHp: number }; effectiveBaseStats: { str: number; con: number } };

// armor_top carries statBonus str+2/con+5. Equipping it must lift the effective base stats and
// derived HP the character sheet shows; unequipping reverts them.
test("equipping an armor applies its stat bonus and unequipping reverts it", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);
  await page.waitForFunction(() => window.mcE2E?.character != null, undefined, { timeout: 10_000, polling: 100 });

  await admin(acct, `/api/admin/players/${acct.charName}/equipment`, {
    method: "PUT",
    body: JSON.stringify({ ownedArmors: ["armor_top"] }),
  });

  const base = (await e2e(page)).character as unknown as CharSync;

  await actions(page).runCommand("/equip armor_top");
  await page.waitForFunction(
    (s) => ((window.mcE2E?.character as unknown as CharSync | null)?.effectiveBaseStats.str ?? 0) === s,
    base.effectiveBaseStats.str + 2,
    { timeout: 10_000, polling: 100 },
  );
  const equipped = (await e2e(page)).character as unknown as CharSync;
  expect(equipped.effectiveBaseStats.con).toBe(base.effectiveBaseStats.con + 5);
  expect(equipped.derived.maxHp, "more CON => more HP").toBeGreaterThan(base.derived.maxHp);

  await actions(page).runCommand("/unequip armor_top");
  await page.waitForFunction(
    (s) => ((window.mcE2E?.character as unknown as CharSync | null)?.effectiveBaseStats.str ?? 0) === s,
    base.effectiveBaseStats.str,
    { timeout: 10_000, polling: 100 },
  );
});
