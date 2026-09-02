/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { SPAWN_X, SPAWN_Z, SETTLED_Y } from "./helpers/constants";

test("login, spawn and the chunk region around it", async ({ page }, info) => {
  const acct = accountFor(info);
  // connectClient already waits for chunk-mesh settle + the spawn fall to finish.
  // recenter=false: this spec asserts the real spawn point, not the shared re-centred pose.
  await connectClient(page, acct, "en", false);

  const s = await e2e(page);
  expect(s.ready).toBe(true);
  expect(s.playerId).toBe(acct.playerId);
  expect(s.playerName).toBe(acct.charName);
  expect(s.stance).toBe("standing");
  expect(s.position.x).toBeCloseTo(SPAWN_X, 0);
  expect(s.position.z).toBeCloseTo(SPAWN_Z, 0);
  expect(s.position.y).toBeCloseTo(SETTLED_Y, 0);

  const key = (c: { cx: number; cz: number }) => `${c.cx},${c.cz}`;
  const loaded = new Set(s.loadedChunks.map(key));
  const meshed = new Set(s.meshedChunks.map(key));

  // The spawn chunk and its four orthogonal neighbours are always in view.
  for (const c of [
    { cx: 0, cz: 0 },
    { cx: 1, cz: 0 },
    { cx: -1, cz: 0 },
    { cx: 0, cz: 1 },
    { cx: 0, cz: -1 },
  ]) {
    expect(loaded.has(key(c)), `chunk ${key(c)} loaded`).toBe(true);
    expect(meshed.has(key(c)), `chunk ${key(c)} meshed`).toBe(true);
  }
  expect(s.loadedChunks.length).toBeGreaterThanOrEqual(5);

  // Nothing beyond the bounded world's void edge (8x8 chunks centred on origin).
  for (const c of s.loadedChunks) {
    expect(c.cx, `cx ${c.cx} in bounds`).toBeGreaterThanOrEqual(-4);
    expect(c.cx, `cx ${c.cx} in bounds`).toBeLessThanOrEqual(3);
    expect(c.cz, `cz ${c.cz} in bounds`).toBeGreaterThanOrEqual(-4);
    expect(c.cz, `cz ${c.cz} in bounds`).toBeLessThanOrEqual(3);
  }
});
