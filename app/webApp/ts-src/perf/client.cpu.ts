/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { execSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { summarizeCpuProfile, type CpuProfile } from "./helpers/cpuProfile";
import type { HeapShare } from "./helpers/heapProfile";
import { enterGame } from "./helpers/perfSession";
import { backToStart, HEADINGS, traverse } from "./helpers/traverse";

const REPO_ROOT = resolve(process.cwd(), "../../..");
const REPORTS_DIR = resolve(REPO_ROOT, ".scratch/perf-baseline/cpu");
const RAW_DIR = resolve(REPO_ROOT, "perf/cpu");
// Fine enough to see per-frame work (a frame is ~16.7 ms), coarse enough not to slow the page much.
const SAMPLING_INTERVAL_US = 200;

const share = (rows: HeapShare[], busyMs: number, durationMs: number) =>
  rows.map(
    (r) =>
      `| ${r.name.replace(/\|/g, "\\|")} | ${(r.bytes / 1000).toFixed(2)} s | ${((r.bytes / durationMs) * 100).toFixed(1)} % | ${r.name === "Idle" ? "—" : `${((r.bytes / busyMs) * 100).toFixed(1)} %`} |`,
  );

test("client main-thread CPU during a traversal", async ({ page }) => {
  await enterGame(page);
  const cdp = await page.context().newCDPSession(page);
  await backToStart(page);

  await cdp.send("Profiler.enable");
  await cdp.send("Profiler.setSamplingInterval", { interval: SAMPLING_INTERVAL_US });
  await cdp.send("Profiler.start");
  await traverse(page, HEADINGS[1]);
  const { profile } = await cdp.send("Profiler.stop");

  const sha = execSync("git rev-parse --short HEAD", { cwd: REPO_ROOT }).toString().trim();
  const stamp = new Date().toISOString().slice(0, 19).replace(/:/g, "");
  mkdirSync(RAW_DIR, { recursive: true });
  mkdirSync(REPORTS_DIR, { recursive: true });
  const rawFile = resolve(RAW_DIR, `${stamp}-${sha}.cpuprofile`);
  writeFileSync(rawFile, JSON.stringify(profile));

  const s = summarizeCpuProfile(profile as CpuProfile);
  const header = "| Where | Self time | Share of wall time | Share of busy time |\n|---|---|---|---|";
  const lines = [
    `# Client CPU — \`${sha}\` (${stamp})`,
    "",
    `One traversal (walk, climb, fly), main thread sampled every ${SAMPLING_INTERVAL_US} µs: ${(s.durationMs / 1000).toFixed(1)} s wall time, ${(s.busyMs / 1000).toFixed(1)} s busy (${((s.busyMs / s.durationMs) * 100).toFixed(0)} %). The chunk-mesh worker runs on its own thread and is not included.`,
    "",
    "## By origin",
    "",
    header,
    ...share(s.bySource, s.busyMs, s.durationMs),
    "",
    "## By function (self time)",
    "",
    header,
    ...share(s.byFunction, s.busyMs, s.durationMs),
    "",
    `Raw profile (Chrome DevTools → Performance → Load profile): \`${rawFile.replace(`${REPO_ROOT}/`, "")}\``,
  ];
  const reportFile = resolve(REPORTS_DIR, `${stamp}-${sha}.md`);
  writeFileSync(reportFile, `${lines.join("\n")}\n`);
  console.log(`[perf] cpu report → ${reportFile}`);
});
