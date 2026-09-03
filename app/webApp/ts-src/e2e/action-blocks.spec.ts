/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { adminWorldContext } from "./helpers/admin";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { GROUND_Y } from "./helpers/constants";
import { actions, waitForInventoryContains } from "./helpers/game";
import { postApiAdminPlayersByNameGive } from "../generated/api/requests";

test("an action block is named, targeted (onTargetEvent), activated (onActivate), then cleaned up on break", async ({
  page,
}, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  await postApiAdminPlayersByNameGive({
    ...adminWorldContext(acct),
    path: { name: acct.charName },
    body: { name: "COBBLESTONE", count: 1 },
  });
  await waitForInventoryContains(page, "COBBLESTONE", 1);

  // Look down so the crosshair lands on the ground block just ahead.
  await actions(page).setLook(0, 1.4);
  await page.waitForFunction((groundY) => window.mcE2E?.targetBlock?.y === groundY, GROUND_Y, {
    timeout: 10_000,
    polling: 100,
  });
  const target = (await e2e(page)).targetBlock!;

  // Name the targeted block (client appends the hovered cell to the command).
  await actions(page).runCommand("/actionblock:activate");
  await page.waitForFunction(
    (t) => (window.mcE2E?.actionBlocks ?? []).some((b) => b.x === t.x && b.y === t.y && b.z === t.z),
    target,
    { timeout: 10_000, polling: 100 },
  );
  const name = (await e2e(page)).actionBlocks.find((b) => b.x === target.x && b.z === target.z)!.name;

  const editField = async (field: string, script: string) => {
    const before = (await e2e(page)).notifications.filter((n) => n.includes("saved")).length;
    await actions(page).runCommand(`/actionblock:edit ${name} ${field} ${script}`);
    await page.waitForFunction(
      (n) => (window.mcE2E?.notifications ?? []).filter((m) => m.includes("saved")).length > n,
      before,
      { timeout: 10_000, polling: 100 },
    );
  };
  await editField("onTargetEvent", "notify('AB-TARGETED')");
  await editField("onActivate", "notify('AB-ACTIVATED')");

  // Tab: unified target cycle should land on the action block and fire onTargetEvent.
  await page.keyboard.press("Tab");
  await page.waitForFunction(
    (t) => {
      const s = window.mcE2E;
      return (
        s?.actionBlockTarget?.x === t.x &&
        s?.actionBlockTarget?.z === t.z &&
        (s?.notifications ?? []).some((n) => n.includes("AB-TARGETED"))
      );
    },
    target,
    { timeout: 10_000, polling: 100 },
  );

  // block_interact (KeyC) on the targeted block fires onActivate.
  await page.keyboard.press("KeyC");
  await page.waitForFunction(
    () => (window.mcE2E?.notifications ?? []).some((n) => n.includes("AB-ACTIVATED")),
    undefined,
    { timeout: 10_000, polling: 100 },
  );

  // Breaking the block removes it from the registry (and the client mirror).
  await actions(page).breakTargeted();
  await page.waitForFunction(
    (t) => !(window.mcE2E?.actionBlocks ?? []).some((b) => b.x === t.x && b.z === t.z),
    target,
    { timeout: 10_000, polling: 100 },
  );

  expect((await e2e(page)).actionBlocks, "no action blocks remain").toHaveLength(0);
});

test("/actionblock:delete removes a named block without breaking it", async ({ page }, info) => {
  const acct = accountFor(info);
  await connectClient(page, acct);

  await actions(page).setLook(0, 1.4);
  await page.waitForFunction((groundY) => window.mcE2E?.targetBlock?.y === groundY, GROUND_Y, {
    timeout: 10_000,
    polling: 100,
  });
  const target = (await e2e(page)).targetBlock!;

  await actions(page).runCommand("/actionblock:activate");
  await page.waitForFunction(
    (t) => (window.mcE2E?.actionBlocks ?? []).some((b) => b.x === t.x && b.z === t.z),
    target,
    { timeout: 10_000, polling: 100 },
  );
  const name = (await e2e(page)).actionBlocks.find((b) => b.x === target.x && b.z === target.z)!.name;

  await actions(page).runCommand(`/actionblock:delete ${name}`);
  await page.waitForFunction(
    (t) => !(window.mcE2E?.actionBlocks ?? []).some((b) => b.x === t.x && b.z === t.z),
    target,
    { timeout: 10_000, polling: 100 },
  );
});
