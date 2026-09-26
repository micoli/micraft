import type { Page } from "@playwright/test";
import { drive } from "./navigator";
import { runCommand, waitForChunksSettled } from "./perfSession";

// Sized so `make perf` (idle + traverse) stays under ~5 min; override for longer windows.
const WALK_MS = Number(process.env.PERF_WALK_MS ?? 8_000);
const FLY_MS = Number(process.env.PERF_FLY_MS ?? 20_000);
const ASCEND_MS = 1_500;

// An open plains spot of the seed-42 world (the default spawn at 8,8 is boxed in by terrain).
const START = { x: Number(process.env.PERF_START_X ?? 200), y: 200, z: Number(process.env.PERF_START_Z ?? -150) };

/** One heading per window, so every repetition streams terrain never generated before. */
export const HEADINGS = [0, Math.PI / 2, Math.PI, (3 * Math.PI) / 2];

// fly_toggle is a Space double-tap (resources/config/keybindings.yaml).
async function toggleFly(page: Page): Promise<void> {
  await page.keyboard.press("Space");
  await page.waitForTimeout(80);
  await page.keyboard.press("Space");
  await page.waitForTimeout(300);
}

/** Walk (jumping), climb, then fly along `yaw`; returns how many times the Character got stuck. */
export async function traverse(page: Page, yaw: number, scale = 1): Promise<number> {
  await page.keyboard.down("KeyW");
  // Walking has no auto-step: holding Space keeps jumping over one-block rises.
  await page.keyboard.down("Space");
  const stuckWalking = await drive(page, yaw, "walk", WALK_MS * scale);
  await page.keyboard.up("Space");

  await toggleFly(page);
  await page.keyboard.down("Space");
  await page.waitForTimeout(ASCEND_MS);
  await page.keyboard.up("Space");
  const stuckFlying = await drive(page, yaw, "fly", FLY_MS * scale);
  await page.keyboard.up("KeyW");
  await toggleFly(page);
  return stuckWalking + stuckFlying;
}

export async function backToStart(page: Page): Promise<void> {
  await runCommand(page, `/teleport ${START.x} ${START.y} ${START.z}`);
  await waitForChunksSettled(page);
}
