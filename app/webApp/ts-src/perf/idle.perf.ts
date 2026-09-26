/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { enterGame, measure, waitForChunksSettled } from "./helpers/perfSession";
import { writeResult } from "./helpers/results";

const WINDOW_MS = Number(process.env.PERF_WINDOW_MS ?? 60_000);
const REPETITIONS = 3;

test("A — idle at spawn", async ({ page }) => {
  await enterGame(page);
  await waitForChunksSettled(page);

  const idle = () => page.waitForTimeout(WINDOW_MS);
  const warmup = await measure(page, idle);
  const runs = [];
  for (let i = 0; i < REPETITIONS; i++) runs.push(await measure(page, idle));

  await writeResult(page, "idle", warmup, runs);
});
