/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { enterGame, measure, waitForChunksSettled } from "./helpers/perfSession";
import { writeResult } from "./helpers/results";

// Sized so `make perf` (idle + traverse) stays under ~5 min; override for longer windows.
const WINDOW_MS = Number(process.env.PERF_WINDOW_MS ?? 15_000);
const WARMUP_MS = Number(process.env.PERF_WARMUP_MS ?? 5_000);
const REPETITIONS = 3;

test("A — idle at spawn", async ({ page }) => {
  await enterGame(page);
  await waitForChunksSettled(page);

  const idle = () => page.waitForTimeout(WINDOW_MS);
  const warmup = await measure(page, () => page.waitForTimeout(WARMUP_MS));
  const runs = [];
  for (let i = 0; i < REPETITIONS; i++) runs.push(await measure(page, idle));

  await writeResult(page, "idle", warmup, runs);
});
