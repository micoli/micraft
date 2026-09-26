/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { accountFor, connectClient, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";

// Terrain meshes keep no CPU geometry in game, so creative mode targets blocks by raycasting the
// client's block data (mcRaycastVoxel), not by picking meshes. A click on the terrain must still
// break the block under the cursor.
test("a click in creative mode breaks the terrain block under the cursor", async ({ page }, info) => {
  await connectClient(page, accountFor(info));

  await actions(page).runCommand("/mode creative");
  await page.waitForFunction(() => window.mcState.editMode === "creative", undefined, { timeout: 10_000 });

  const canvas = page.locator("#renderCanvas");
  const box = (await canvas.boundingBox())!;
  const center = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
  await page.mouse.move(center.x, center.y);
  await page.mouse.click(center.x, center.y);

  const broken = await page.waitForFunction(
    () => window.mcE2E?.lastWorldUpdate?.find((c) => c.block.toLowerCase() === "air") ?? null,
    undefined,
    { timeout: 15_000, polling: 100 },
  );
  expect(await broken.jsonValue(), "the clicked terrain block became air").not.toBeNull();
});
