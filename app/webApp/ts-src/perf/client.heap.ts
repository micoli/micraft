/// <reference path="../global.d.ts" />
import { test, type CDPSession, type Page } from "@playwright/test";
import { execSync } from "node:child_process";
import { createWriteStream, mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { summarizeHeapProfile, type HeapShare, type SamplingHeapProfile } from "./helpers/heapProfile";
import { enterGame } from "./helpers/perfSession";
import { backToStart, HEADINGS, traverse } from "./helpers/traverse";

const REPO_ROOT = resolve(process.cwd(), "../../..");
const REPORTS_DIR = resolve(REPO_ROOT, ".scratch/perf-baseline/heap");
const RAW_DIR = resolve(REPO_ROOT, "perf/heap");
// 32 KiB is Chrome's default; finer sampling slows the page it measures.
const SAMPLING_INTERVAL_BYTES = 32 * 1024;
const MB = 1024 * 1024;

interface HeapReading {
  usedBytes: number;
  totalBytes: number;
  /** ArrayBuffer backing stores (vertex data, typed arrays): outside the JS heap figures. */
  backingBytes: number;
}

const heapUsage = async (cdp: CDPSession): Promise<HeapReading> => {
  const { usedSize, totalSize, backingStorageSize } = await cdp.send("Runtime.getHeapUsage");
  return { usedBytes: usedSize, totalBytes: totalSize, backingBytes: backingStorageSize };
};

/** The heap as the perf runs see it, then after a forced full GC: the gap is garbage, not a leak. */
async function readHeap(cdp: CDPSession): Promise<{ beforeGc: HeapReading; afterGc: HeapReading }> {
  const beforeGc = await heapUsage(cdp);
  await cdp.send("HeapProfiler.collectGarbage");
  return { beforeGc, afterGc: await heapUsage(cdp) };
}

async function writeFullSnapshot(cdp: CDPSession, file: string): Promise<void> {
  const out = createWriteStream(file);
  cdp.on("HeapProfiler.addHeapSnapshotChunk", ({ chunk }) => out.write(chunk));
  await cdp.send("HeapProfiler.takeHeapSnapshot", { reportProgress: false });
  await new Promise<void>((done) => out.end(done));
}

const mb = (bytes: number) => `${(bytes / MB).toFixed(0)} MB`;
const share = (rows: HeapShare[], total: number) =>
  rows.map((r) => `| ${r.name.replace(/\|/g, "\\|")} | ${mb(r.bytes)} | ${((r.bytes / total) * 100).toFixed(1)} % |`);

async function report(
  page: Page,
  phases: Record<string, Awaited<ReturnType<typeof readHeap>>>,
  profile: SamplingHeapProfile,
) {
  const sha = execSync("git rev-parse --short HEAD", { cwd: REPO_ROOT }).toString().trim();
  const stamp = new Date().toISOString().slice(0, 19).replace(/:/g, "");
  mkdirSync(RAW_DIR, { recursive: true });
  mkdirSync(REPORTS_DIR, { recursive: true });
  const rawFile = resolve(RAW_DIR, `${stamp}-${sha}.heapprofile`);
  writeFileSync(rawFile, JSON.stringify(profile));

  const summary = summarizeHeapProfile(profile);
  const perfMemory = await page.evaluate(() => window.mcPerf?.snapshot().memory);
  const lines = [
    `# Client heap — \`${sha}\` (${stamp})`,
    "",
    "## JS heap before / after a forced GC",
    "",
    "| Moment | Used before GC | Used after GC (live) | Garbage | Heap reserved | ArrayBuffers (live) |",
    "|---|---|---|---|---|---|",
    ...Object.entries(phases).map(
      ([moment, h]) =>
        `| ${moment} | ${mb(h.beforeGc.usedBytes)} | ${mb(h.afterGc.usedBytes)} | ${mb(h.beforeGc.usedBytes - h.afterGc.usedBytes)} | ${mb(h.afterGc.totalBytes)} | ${mb(h.afterGc.backingBytes)} |`,
    ),
    "",
    `Collector memory reading at the end: ${JSON.stringify(perfMemory)}`,
    "",
    `## Live allocations by origin (sampled every ${SAMPLING_INTERVAL_BYTES / 1024} KiB, still alive at the end)`,
    "",
    `Sampled live total: ${mb(summary.totalBytes)}.`,
    "",
    "| Origin | Live | Share |",
    "|---|---|---|",
    ...share(summary.bySource, summary.totalBytes),
    "",
    "## Live allocations by allocating function (self)",
    "",
    "| Function | Live | Share |",
    "|---|---|---|",
    ...share(summary.byFunction, summary.totalBytes),
    "",
    `Raw profile (open in Chrome DevTools → Memory → Load): \`${rawFile.replace(`${REPO_ROOT}/`, "")}\``,
  ];
  const reportFile = resolve(REPORTS_DIR, `${stamp}-${sha}.md`);
  writeFileSync(reportFile, `${lines.join("\n")}\n`);
  console.log(`[perf] heap report → ${reportFile}`);
}

test("client heap after a traversal", async ({ page }) => {
  await enterGame(page);
  const cdp = await page.context().newCDPSession(page);
  await backToStart(page);

  const phases: Record<string, Awaited<ReturnType<typeof readHeap>>> = {};
  phases["At start, settled"] = await readHeap(cdp);
  await cdp.send("HeapProfiler.startSampling", { samplingInterval: SAMPLING_INTERVAL_BYTES });

  for (const heading of HEADINGS.slice(0, 2)) {
    await backToStart(page);
    await traverse(page, heading);
  }
  phases["After 2 traversals, far away"] = await readHeap(cdp);
  await backToStart(page);
  phases["Back at start, settled"] = await readHeap(cdp);

  const { profile } = await cdp.send("HeapProfiler.stopSampling");
  await report(page, phases, profile as SamplingHeapProfile);
  if (process.env.PERF_HEAP_SNAPSHOT) {
    const file = resolve(RAW_DIR, `${Date.now()}.heapsnapshot`);
    await writeFullSnapshot(cdp, file);
    console.log(`[perf] full heap snapshot → ${file}`);
  }
});
