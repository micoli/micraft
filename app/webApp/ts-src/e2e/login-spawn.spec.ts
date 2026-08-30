import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { SPAWN_X, SPAWN_Z, SETTLED_Y, expectedChunkHalo } from "./helpers/constants";

test("login, spawn and the chunk halo around it", async ({ page }, info) => {
  const acct = accountFor(info.parallelIndex);
  await connectClient(page, acct);

  const s = await e2e(page);
  expect(s.ready).toBe(true);
  expect(s.playerId).toMatch(/.+/);
  expect(s.playerName).toBe(acct.charName);
  expect(s.stance).toBe("standing");
  expect(s.position.x).toBeCloseTo(SPAWN_X, 0);
  expect(s.position.z).toBeCloseTo(SPAWN_Z, 0);
  expect(s.position.y).toBeCloseTo(SETTLED_Y, 0);

  const key = (c: { cx: number; cz: number }) => `${c.cx},${c.cz}`;
  const loaded = new Set(s.loadedChunks.map(key));
  const meshed = new Set(s.meshedChunks.map(key));
  for (const c of expectedChunkHalo()) {
    expect(loaded.has(key(c)), `chunk ${key(c)} loaded`).toBe(true);
    expect(meshed.has(key(c)), `chunk ${key(c)} meshed`).toBe(true);
  }
  // Nothing beyond the bounded world's void edge.
  for (const c of s.loadedChunks) {
    expect(c.cx).toBeGreaterThanOrEqual(-4);
    expect(c.cx).toBeLessThanOrEqual(3);
    expect(c.cz).toBeGreaterThanOrEqual(-4);
    expect(c.cz).toBeLessThanOrEqual(3);
  }
});
