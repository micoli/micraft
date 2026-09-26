/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { enterGame, measure } from "./helpers/perfSession";
import { writeResult } from "./helpers/results";
import { backToStart, HEADINGS, traverse } from "./helpers/traverse";

// The warm-up only needs to exercise the code paths once.
const WARMUP_SCALE = 0.5;

test("B — traverse ungenerated terrain", async ({ page }) => {
  await enterGame(page);
  await backToStart(page);

  const [warmupHeading, ...runHeadings] = HEADINGS;
  const warmup = await measure(page, () => traverse(page, warmupHeading, WARMUP_SCALE));
  const runs = [];
  for (const heading of runHeadings) {
    await backToStart(page);
    runs.push(await measure(page, () => traverse(page, heading)));
  }

  await writeResult(page, "traverse", warmup, runs);
});
