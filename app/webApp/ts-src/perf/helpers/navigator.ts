/// <reference path="../../global.d.ts" />
import type { Page } from "@playwright/test";
import { initialSteering, steer, type SteeringState } from "./steering";

const SAMPLE_EVERY_MS = 500;
const PROGRESS_WINDOW_MS = 1_500;
const BACKOFF_MS = 800;
const ASCEND_MS = 1_000;

interface Sample {
  t: number;
  x: number;
  z: number;
}

const TURN_TOLERANCE_RAD = 0.05;
const TURN_TIMEOUT_MS = 4_000;

export const cameraXz = (page: Page) =>
  page.evaluate(() => {
    const p = (window.mcState.engine as import("@babylonjs/core").Engine).scenes[0].activeCamera!.position;
    return { x: p.x, z: p.z };
  });

const cameraYaw = (page: Page) =>
  page.evaluate(
    () =>
      (
        (window.mcState.engine as import("@babylonjs/core").Engine).scenes[0]
          .activeCamera as import("@babylonjs/core").TargetCamera
      ).rotation.y,
  );

const angleDiff = (target: number, current: number) =>
  Math.atan2(Math.sin(target - current), Math.cos(target - current));

/**
 * Turn to `yaw` the way a player does: hold rotate_left / rotate_right (A / D) until the camera faces
 * it. `mcE2E.actions.setLook` only applies inside an E2E session, which the perf runs don't open.
 */
export async function look(page: Page, yaw: number): Promise<void> {
  const deadline = Date.now() + TURN_TIMEOUT_MS;
  let diff = angleDiff(yaw, await cameraYaw(page));
  while (Math.abs(diff) > TURN_TOLERANCE_RAD && Date.now() < deadline) {
    const key = diff > 0 ? "KeyD" : "KeyA";
    await page.keyboard.down(key);
    while (Date.now() < deadline) {
      await page.waitForTimeout(20);
      const next = angleDiff(yaw, await cameraYaw(page));
      if (Math.abs(next) <= TURN_TOLERANCE_RAD || Math.sign(next) !== Math.sign(diff)) break;
    }
    await page.keyboard.up(key);
    diff = angleDiff(yaw, await cameraYaw(page));
  }
}

/**
 * Hold W toward `targetYaw` for `durationMs`, detouring around whatever stops the Character (a
 * trunk, a canopy over its head, a cliff) — see `steer`. The movement keys must already be down.
 * Returns how many times it got stuck.
 */
export async function drive(page: Page, targetYaw: number, mode: "walk" | "fly", durationMs: number): Promise<number> {
  let state: SteeringState = initialSteering(targetYaw);
  let samples: Sample[] = [];
  await look(page, state.yaw);
  const end = Date.now() + durationMs;

  while (Date.now() < end) {
    await page.waitForTimeout(SAMPLE_EVERY_MS);
    const now = Date.now();
    samples.push({ t: now, ...(await cameraXz(page)) });
    const reference = samples.find((s) => now - s.t >= PROGRESS_WINDOW_MS);
    if (!reference) continue;
    samples = samples.filter((s) => now - s.t < PROGRESS_WINDOW_MS + SAMPLE_EVERY_MS);
    const last = samples[samples.length - 1];
    const progressBlocks = Math.hypot(last.x - reference.x, last.z - reference.z);

    const r = steer(state, { nowMs: now, progressBlocks, mode });
    state = r.state;
    if (r.action === "none") continue;
    if (r.action === "backoff") {
      await page.keyboard.up("KeyW");
      await page.keyboard.down("KeyS");
      await page.waitForTimeout(BACKOFF_MS);
      await page.keyboard.up("KeyS");
      await page.keyboard.down("KeyW");
    }
    if (r.action === "ascend") {
      await page.keyboard.down("Space");
      await page.waitForTimeout(ASCEND_MS);
      await page.keyboard.up("Space");
    }
    await look(page, state.yaw);
    samples = [];
  }
  return state.stuckEvents;
}
