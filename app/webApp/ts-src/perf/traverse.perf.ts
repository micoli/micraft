/// <reference path="../global.d.ts" />
import { test, type Page } from "@playwright/test";
import { enterGame, measure, runCommand, waitForChunksSettled } from "./helpers/perfSession";
import { writeResult } from "./helpers/results";

const WALK_MS = Number(process.env.PERF_WALK_MS ?? 30_000);
const FLY_MS = Number(process.env.PERF_FLY_MS ?? 60_000);
const ASCEND_MS = 3_000;

// An open plains spot of the seed-42 world (the default spawn at 8,8 is boxed in by terrain).
// Each window heads a different way so every repetition streams terrain never generated before.
const START = { x: Number(process.env.PERF_START_X ?? 200), y: 200, z: Number(process.env.PERF_START_Z ?? -150) };
const HEADINGS = [0, Math.PI / 2, Math.PI, (3 * Math.PI) / 2];

// fly_toggle is a Space double-tap (resources/config/keybindings.yaml).
async function toggleFly(page: Page): Promise<void> {
  await page.keyboard.press("Space");
  await page.waitForTimeout(80);
  await page.keyboard.press("Space");
  await page.waitForTimeout(300);
}

async function traverse(page: Page, yaw: number): Promise<void> {
  await page.evaluate((y) => window.mcE2E!.actions!.setLook(y, 0), yaw);
  await page.keyboard.down("KeyW");
  // Walking has no auto-step: holding Space keeps jumping over one-block rises.
  await page.keyboard.down("Space");
  await page.waitForTimeout(WALK_MS);
  await page.keyboard.up("Space");

  await toggleFly(page);
  await page.keyboard.down("Space");
  await page.waitForTimeout(ASCEND_MS);
  await page.keyboard.up("Space");
  await page.waitForTimeout(FLY_MS);
  await page.keyboard.up("KeyW");
  await toggleFly(page);
}

async function backToStart(page: Page): Promise<void> {
  await runCommand(page, `/teleport ${START.x} ${START.y} ${START.z}`);
  await waitForChunksSettled(page);
}

test("B — traverse ungenerated terrain", async ({ page }) => {
  await enterGame(page);
  await backToStart(page);

  const [warmupHeading, ...runHeadings] = HEADINGS;
  const warmup = await measure(page, () => traverse(page, warmupHeading));
  const runs = [];
  for (const heading of runHeadings) {
    await backToStart(page);
    runs.push(await measure(page, () => traverse(page, heading)));
  }

  await writeResult(page, "traverse", warmup, runs);
});
