/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";

type CharSync = {
  character: { characterClass: string; level: number; baseStats: { str: number; con: number } };
  derived: { maxHp: number };
};

// CharacterSync drives the sheet: the WARRIOR class bonus (+2 STR, +1 CON) must be baked into
// the level-1 point-buy (all 8) base stats, and derived maxHp must match the HUD vitals.
test("CharacterSync exposes class bonuses and derived stats consistent with vitals", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  await page.waitForFunction(() => window.mcE2E?.character != null && (window.mcE2E?.playerStatus?.maxHp ?? 0) > 0, undefined, {
    timeout: 10_000,
    polling: 100,
  });

  const s = await e2e(page);
  const c = s.character as unknown as CharSync;

  expect(c.character.characterClass).toBe("WARRIOR");
  expect(c.character.level, "fresh character is level 1").toBe(1);
  expect(c.character.baseStats.str, "8 point-buy + 2 WARRIOR bonus").toBe(10);
  expect(c.character.baseStats.con, "8 point-buy + 1 WARRIOR bonus").toBe(9);
  expect(c.derived.maxHp, "derived maxHp feeds the vitals").toBe(s.playerStatus!.maxHp);
});
