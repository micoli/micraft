#!/usr/bin/env node
// Turns perf result JSONs (written by `make perf`) into a Markdown report: frame and GPU times in
// ms and FPS, render load, client and server memory, server tick per phase, budgets, stability
// across invocations, and a comparison against a base commit.
//
//   node perf/analyze.mjs                    # every result of the most recent commit measured
//   node perf/analyze.mjs --sha <sha>        # every result of that commit
//   node perf/analyze.mjs --base <sha>       # also compare against that commit's results
//   node perf/analyze.mjs <file.json> ...    # explicit files
import { readdirSync, readFileSync } from "node:fs";
import { basename, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RESULTS_DIR = resolve(HERE, "../../../../.scratch/perf-baseline/results");
const BUDGETS = JSON.parse(readFileSync(resolve(HERE, "budgets.json"), "utf8"));
const TICK_BUDGET_MS = 50;
const MB = 1_048_576;

// ── inputs ──────────────────────────────────────────────────────────────────────────────────────

function parseArgs(argv) {
  const args = { files: [], sha: null, base: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--sha") args.sha = argv[++i];
    else if (argv[i] === "--base") args.base = argv[++i];
    else args.files.push(argv[i]);
  }
  return args;
}

const allResultFiles = () =>
  readdirSync(RESULTS_DIR)
    .filter((f) => f.endsWith(".json"))
    .sort()
    .map((f) => resolve(RESULTS_DIR, f));

const load = (file) => ({ file: basename(file), ...JSON.parse(readFileSync(file, "utf8")) });

function selectResults(args) {
  if (args.files.length > 0) return args.files.map(load);
  const all = allResultFiles().map(load);
  if (all.length === 0) throw new Error(`no result in ${RESULTS_DIR} — run \`make perf\` first`);
  const sha = args.sha ?? all[all.length - 1].gitSha;
  return all.filter((r) => r.gitSha === sha);
}

const baseResults = (sha) =>
  sha
    ? allResultFiles()
        .map(load)
        .filter((r) => r.gitSha === sha)
    : [];

// ── maths & formatting ──────────────────────────────────────────────────────────────────────────

const median = (values) => {
  const s = [...values].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length === 0 ? NaN : s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};
const min = (v) => Math.min(...v);
const max = (v) => Math.max(...v);
const spreadPct = (v) => (median(v) === 0 ? 0 : ((max(v) - min(v)) / median(v)) * 100);
const deltaPct = (head, base) => (base === 0 ? 0 : ((head - base) / base) * 100);

const ms = (v) => `${v.toFixed(1)} ms`;
const fps = (frameMs) => (frameMs > 0 ? `${(1000 / frameMs).toFixed(0)} FPS` : "—");
const mb = (bytes) => `${(bytes / MB).toFixed(0)} MB`;
const pct = (v) => `${v >= 0 ? "+" : ""}${v.toFixed(1)} %`;
const ok = (pass) => (pass ? "✓" : "✗");
const table = (header, rows) =>
  [
    `| ${header.join(" | ")} |`,
    `|${header.map(() => "---").join("|")}|`,
    ...rows.map((r) => `| ${r.join(" | ")} |`),
  ].join("\n");

// ── per-window accessors ────────────────────────────────────────────────────────────────────────

/** Measured windows (warm-up excluded), each tagged with its invocation number. */
const windowsOf = (results) => results.flatMap((r, i) => r.runs.map((w) => ({ ...w, invocation: i + 1 })));

/** A traverse window blocked below half the median travel, or an idle window that moved, measured something else. */
function validWindows(scenario, windows) {
  if (scenario === "idle") return windows.filter((w) => w.travelledBlocks <= 1);
  const medianTravel = median(windows.map((w) => w.travelledBlocks));
  return windows.filter((w) => w.travelledBlocks >= medianTravel * 0.5);
}

/** Live heap (after a forced GC) when recorded; older results only have the in-use heap, garbage included. */
const liveHeap = (w) => w.clientLiveHeapBytes ?? NaN;
const hasLiveHeap = (windows) => windows.every((w) => w.clientLiveHeapBytes !== undefined);
const growthHeap = (windows) => (hasLiveHeap(windows) ? liveHeap : (w) => w.client.memory.jsHeapUsedBytes ?? 0);

/** Client JS heap growth first → last window, per invocation (the heap starts over each run), median across them. */
function heapGrowthPct(windows) {
  const heapOf = growthHeap(windows);
  const growths = [...new Set(windows.map((w) => w.invocation))].map((inv) => {
    const heap = windows.filter((w) => w.invocation === inv).map(heapOf);
    return deltaPct(heap[heap.length - 1], heap[0]);
  });
  return median(growths);
}
const phase = (w, name) => w.server.tick.find((p) => p.name === name);
const seconds = (w) => w.client.durationMs / 1000;
const avgFps = (w) => (w.client.frames * 1000) / w.client.durationMs;

/** Median across windows, with the min–max range, of `f(window)`. */
/** Share of frames that missed a display refresh; NaN for results recorded before the collector counted them. */
const longFramePct = (w) => (w.client.longFrames === undefined ? NaN : (w.client.longFrames / w.client.frames) * 100);

function across(windows, f) {
  const v = windows.map(f).filter((x) => Number.isFinite(x));
  return { median: median(v), min: min(v), max: max(v) };
}

// ── report sections ─────────────────────────────────────────────────────────────────────────────

function contextSection(results) {
  const r = results[0];
  return [
    `- Commit \`${r.gitSha}\`${r.gitDirty ? " **(dirty tree — not a clean baseline)**" : ""}`,
    `- Invocations: ${results.map((x) => `\`${x.file}\``).join(", ")}`,
    `- Measured windows: ${windowsOf(results).length} (warm-up excluded), ${across(windowsOf(results), seconds).median.toFixed(0)} s each`,
    `- Machine: ${r.machine.cpu} ×${r.machine.cores}, ${mb(r.machine.memBytes)} RAM, ${r.machine.os}, GPU ${r.machine.gpu}`,
    `- Config: encoder ${r.config.messageEncoder}, viewport ${r.config.viewport?.width}×${r.config.viewport?.height}`,
  ].join("\n");
}

function validitySection(scenario, windows) {
  const valid = new Set(validWindows(scenario, windows));
  const rows = windows.map((w, i) => [
    `#${i + 1}`,
    `${w.invocation}`,
    `${seconds(w).toFixed(0)} s`,
    `${w.client.frames}`,
    `${w.travelledBlocks.toFixed(0)}`,
    `${w.stuckEvents}`,
    `${w.server.loadedChunks}`,
    valid.has(w) ? "✓" : "✗ excluded",
  ]);
  const warnings = [];
  const excluded = windows.length - valid.size;
  if (excluded > 0) {
    warnings.push(
      scenario === "idle"
        ? `${excluded} window(s) excluded: the Character moved during idle`
        : `${excluded} window(s) excluded: travelled < 50 % of the median (blocked despite steering)`,
    );
  }
  if (scenario === "traverse" && median(windows.map((w) => w.travelledBlocks)) < 30) {
    warnings.push(
      "median travel < 30 blocks — the traversal did not stream new terrain; the whole scenario is suspect",
    );
  }
  return [
    table(
      ["Window", "Invocation", "Duration", "Frames", "Travelled (blocks)", "Stuck", "Loaded chunks", "Valid"],
      rows,
    ),
    warnings.length
      ? `${warnings.map((w) => `> ⚠️ ${w}`).join("\n")}\n\nEvery section below uses the valid windows only.`
      : "Valid: every window ran as scripted.",
  ].join("\n\n");
}

function frameSection(windows) {
  const rows = ["p50", "p95", "p99", "max"].map((k) => {
    const a = across(windows, (w) => w.client.frameMs[k]);
    return [k, ms(a.median), fps(a.median), `${ms(a.min)} – ${ms(a.max)}`, `${fps(a.max)} – ${fps(a.min)}`];
  });
  const avg = across(windows, avgFps);
  rows.unshift([
    "average",
    ms(1000 / avg.median),
    `${avg.median.toFixed(1)} FPS`,
    "",
    `${avg.min.toFixed(1)} – ${avg.max.toFixed(1)} FPS`,
  ]);
  const long = across(windows, longFramePct);
  rows.push([
    "long frames (> 1.5 × p50)",
    Number.isNaN(long.median) ? "—" : `${long.median.toFixed(2)} %`,
    "",
    "",
    Number.isNaN(long.median) ? "not recorded" : `${long.min.toFixed(2)} – ${long.max.toFixed(2)} %`,
  ]);
  const gpuWindows = windows.filter((w) => w.client.gpuFrameMs);
  const gpu = gpuWindows.length
    ? table(
        ["GPU frame time", "Median", "GPU-bound ceiling", "Range across windows"],
        ["p50", "p95", "p99", "max"].map((k) => {
          const a = across(gpuWindows, (w) => w.client.gpuFrameMs[k]);
          return [k, ms(a.median), fps(a.median), `${ms(a.min)} – ${ms(a.max)}`];
        }),
      )
    : "GPU timer queries unavailable on this GPU/browser — no GPU frame time.";
  return [
    table(["Frame time", "Median", "Equivalent", "Range (ms)", "Range (FPS)"], rows),
    gpu,
    "FPS = 1000 / frame time. The render loop is capped at ~14 ms and paced by the display, so p50 ≈ 16.7 ms (60 FPS) is the ceiling on a 60 Hz / 120 Hz screen. The gap between frame time and GPU time is CPU time (game tick, meshing, JS/Wasm GC).",
  ].join("\n\n");
}

function renderLoadSection(windows) {
  const row = (label, f) => {
    const a = across(windows, f);
    return [label, a.median.toFixed(0), `${a.min.toFixed(0)} – ${a.max.toFixed(0)}`];
  };
  return table(
    ["Metric (per frame)", "Median", "Range across windows"],
    [
      row("Draw calls (avg)", (w) => w.client.drawCalls.avg),
      row("Draw calls (max)", (w) => w.client.drawCalls.max),
      row("Active meshes (avg)", (w) => w.client.activeMeshes.avg),
      row("Active indices (avg)", (w) => w.client.activeIndices.avg),
      row("Triangles (avg)", (w) => w.client.activeIndices.avg / 3),
      row("Textures", (w) => w.client.textures),
    ],
  );
}

function memorySection(windows) {
  const growth = heapGrowthPct(windows);
  const rows = windows.map((w, i) => [
    `#${i + 1} (inv. ${w.invocation})`,
    mb(w.client.memory.jsHeapUsedBytes ?? 0),
    Number.isNaN(liveHeap(w)) ? "—" : mb(liveHeap(w)),
    w.clientArrayBufferBytes === undefined ? "—" : mb(w.clientArrayBufferBytes),
    mb(w.client.memory.wasmMemoryBytes ?? 0),
    mb(w.client.memory.gpuBufferBytes ?? 0),
    mb(w.server.process.heapMaxBytes),
  ]);
  return [
    table(
      [
        "Window",
        "Client JS heap (in use)",
        "Client JS heap (live, after GC)",
        "Client ArrayBuffers (live)",
        "Client Wasm memory",
        "Client GPU buffers",
        "Server heap (max)",
      ],
      rows,
    ),
    `Client JS heap growth first → last window of each invocation (median, ${hasLiveHeap(windows) ? "live heap" : "in-use heap — live heap not recorded"}): ${pct(growth)} (budget ≤ ${BUDGETS.clientJsHeapGrowthPct} %). The in-use heap includes garbage not yet collected and swings with GC timing; the live heap is read after a forced GC at the end of each window; ArrayBuffer backing stores (typed arrays, vertex data) are counted apart from the JS heap. Kotlin/Wasm uses WasmGC, so its objects are in the JS heap and Wasm linear memory stays ~0.`,
  ].join("\n\n");
}

function tickSection(windows) {
  const total = ["p50", "p95", "p99", "max"].map((k) => {
    const a = across(windows, (w) => phase(w, "total")?.[`${k}Ms`] ?? 0);
    return [k, ms(a.median), `${((a.median / TICK_BUDGET_MS) * 100).toFixed(1)} %`, `${ms(a.min)} – ${ms(a.max)}`];
  });
  const names = [...new Set(windows.flatMap((w) => w.server.tick.map((p) => p.name)))].filter((n) => n !== "total");
  const phases = names
    .map((name) => ({ name, p95: across(windows, (w) => phase(w, name)?.p95Ms ?? 0).median, w: windows }))
    .sort((a, b) => b.p95 - a.p95)
    .map(({ name }) => {
      const m = (k) => across(windows, (w) => phase(w, name)?.[`${k}Ms`] ?? 0).median;
      const count = across(windows, (w) => phase(w, name)?.count ?? 0).median;
      return [name, count.toFixed(0), ms(m("p50")), ms(m("p95")), ms(m("p99")), ms(m("max"))];
    });
  const ticks = across(windows, (w) => (phase(w, "total")?.count ?? 0) / seconds(w));
  return [
    table(["Server tick (total)", "Median", "Share of the 50 ms budget", "Range across windows"], total),
    `Tick rate: ${ticks.median.toFixed(1)} TPS (target 20).`,
    table(["Phase (sorted by p95)", "Samples", "p50", "p95", "p99", "max"], phases),
    "A phase with fewer samples than `total` runs every N ticks, so its percentiles describe the ticks where it ran.",
  ].join("\n\n");
}

function processSection(windows) {
  const row = (label, f, fmt) => {
    const a = across(windows, f);
    return [label, fmt(a.median), `${fmt(a.min)} – ${fmt(a.max)}`];
  };
  const kbps = (v) => `${(v / 1024).toFixed(1)} KB/s`;
  return table(
    ["Server process", "Median", "Range across windows"],
    [
      row("Heap min", (w) => w.server.process.heapMinBytes, mb),
      row("Heap avg", (w) => w.server.process.heapAvgBytes, mb),
      row("Heap max", (w) => w.server.process.heapMaxBytes, mb),
      row(
        "CPU load (process)",
        (w) => w.server.process.cpuLoadAvg * 100,
        (v) => `${v.toFixed(1)} %`,
      ),
      row(
        "GC collections",
        (w) => w.server.process.gcCollections,
        (v) => v.toFixed(0),
      ),
      row(
        "GC time",
        (w) => w.server.process.gcTimeMs,
        (v) => `${v.toFixed(0)} ms`,
      ),
      row(
        "GC share of wall time",
        (w) => (w.server.process.gcTimeMs / w.server.process.durationMs) * 100,
        (v) => `${v.toFixed(2)} %`,
      ),
      row("Network out", (w) => w.server.process.networkBytesOut / seconds(w), kbps),
      row("Network in", (w) => w.server.process.networkBytesIn / seconds(w), kbps),
      row(
        "Loaded chunks",
        (w) => w.server.loadedChunks,
        (v) => v.toFixed(0),
      ),
      row(
        "NPCs",
        (w) => w.server.npcs,
        (v) => v.toFixed(0),
      ),
    ],
  );
}

function headline(windows) {
  const heap = windows.map((w) => w.client.memory.jsHeapUsedBytes ?? 0);
  return {
    "Frame p50 (ms)": across(windows, (w) => w.client.frameMs.p50).median,
    "Frame p95 (ms)": across(windows, (w) => w.client.frameMs.p95).median,
    "Frame p99 (ms)": across(windows, (w) => w.client.frameMs.p99).median,
    "Long frames (%)": across(windows, longFramePct).median,
    "Average FPS": across(windows, avgFps).median,
    "GPU p95 (ms)": across(windows, (w) => w.client.gpuFrameMs?.p95 ?? NaN).median,
    "Draw calls (avg)": across(windows, (w) => w.client.drawCalls.avg).median,
    "Tick p50 (ms)": across(windows, (w) => phase(w, "total")?.p50Ms ?? 0).median,
    "Tick p95 (ms)": across(windows, (w) => phase(w, "total")?.p95Ms ?? 0).median,
    "Tick p99 (ms)": across(windows, (w) => phase(w, "total")?.p99Ms ?? 0).median,
    "Client JS heap (MB)": median(heap) / MB,
    "Client JS heap live (MB)": across(windows, liveHeap).median / MB,
    "Client ArrayBuffers live (MB)": across(windows, (w) => w.clientArrayBufferBytes ?? NaN).median / MB,
    "Server heap max (MB)": across(windows, (w) => w.server.process.heapMaxBytes).median / MB,
  };
}

function budgetSection(windows) {
  const h = headline(windows);
  const growth = heapGrowthPct(windows);
  const rows = [
    [
      "Long frames (missed refresh)",
      Number.isNaN(h["Long frames (%)"]) ? "not recorded" : `${h["Long frames (%)"].toFixed(2)} %`,
      `≤ ${BUDGETS.clientLongFramePct} %`,
      Number.isNaN(h["Long frames (%)"]) ? "—" : ok(h["Long frames (%)"] <= BUDGETS.clientLongFramePct),
    ],
    [
      "Frame p95",
      `${ms(h["Frame p95 (ms)"])} (${fps(h["Frame p95 (ms)"])})`,
      `≤ ${ms(BUDGETS.clientFrameP95Ms)} (${fps(BUDGETS.clientFrameP95Ms)})`,
      ok(h["Frame p95 (ms)"] <= BUDGETS.clientFrameP95Ms),
    ],
    [
      "Frame p99",
      `${ms(h["Frame p99 (ms)"])} (${fps(h["Frame p99 (ms)"])})`,
      `< ${ms(BUDGETS.clientFrameP99Ms)} (${fps(BUDGETS.clientFrameP99Ms)})`,
      ok(h["Frame p99 (ms)"] < BUDGETS.clientFrameP99Ms),
    ],
    [
      "Tick p50",
      ms(h["Tick p50 (ms)"]),
      `< ${ms(BUDGETS.serverTickP50Ms)}`,
      ok(h["Tick p50 (ms)"] < BUDGETS.serverTickP50Ms),
    ],
    [
      "Tick p99",
      ms(h["Tick p99 (ms)"]),
      `< ${ms(BUDGETS.serverTickP99Ms)}`,
      ok(h["Tick p99 (ms)"] < BUDGETS.serverTickP99Ms),
    ],
    [
      "Client JS heap growth",
      pct(growth),
      `≤ ${BUDGETS.clientJsHeapGrowthPct} %`,
      ok(growth <= BUDGETS.clientJsHeapGrowthPct),
    ],
  ];
  return table(["Budget", "Measured", "Target", "Pass"], rows);
}

function stabilitySection(windows) {
  const invocations = [...new Set(windows.map((w) => w.invocation))];
  if (invocations.length < 2) return "Single invocation — run `make perf` twice on the same commit to check stability.";
  const perInvocation = invocations.map((inv) => headline(windows.filter((w) => w.invocation === inv)));
  const recorded = (k) => perInvocation.every((h) => !Number.isNaN(h[k]));
  const rows = Object.keys(perInvocation[0])
    .filter(recorded)
    .map((k) => {
      const v = perInvocation.map((h) => h[k]);
      const s = spreadPct(v);
      return [k, v.map((x) => x.toFixed(2)).join(" / "), `${s.toFixed(1)} %`, ok(s < BUDGETS.stabilitySpreadPct)];
    });
  return table(["Metric", "Per invocation", "Spread", `< ${BUDGETS.stabilitySpreadPct} %`], rows);
}

function comparisonSection(headWindows, baseWindows, baseSha) {
  if (baseWindows.length === 0) return null;
  const head = headline(headWindows);
  const base = headline(baseWindows);
  const lowerIsBetter = (k) => k !== "Average FPS";
  const recorded = (k) => !Number.isNaN(head[k]) && !Number.isNaN(base[k]);
  const rows = Object.keys(head)
    .filter(recorded)
    .map((k) => {
      const d = deltaPct(head[k], base[k]);
      const better = lowerIsBetter(k) ? d < 0 : d > 0;
      const verdict = Math.abs(d) < BUDGETS.stabilitySpreadPct ? "≈ noise" : better ? "better" : "worse";
      return [k, base[k].toFixed(2), head[k].toFixed(2), pct(d), verdict];
    });
  return [
    `Against \`${baseSha}\` (differences under ${BUDGETS.stabilitySpreadPct} % are within run-to-run noise):`,
    table(["Metric", "Base", "Head", "Δ", "Verdict"], rows),
  ].join("\n\n");
}

// ── main ────────────────────────────────────────────────────────────────────────────────────────

const args = parseArgs(process.argv.slice(2));
const results = selectResults(args);
const bases = baseResults(args.base);
const out = [`# Perf report — \`${results[0].gitSha}\``];
for (const scenario of [...new Set(results.map((r) => r.scenario))]) {
  const rs = results.filter((r) => r.scenario === scenario);
  const windows = validWindows(scenario, windowsOf(rs));
  out.push(`## Scenario: ${scenario}`, contextSection(rs));
  out.push("### Validity", validitySection(scenario, windowsOf(rs)));
  out.push("### Frame time (ms and FPS)", frameSection(windows));
  out.push("### Render load", renderLoadSection(windows));
  out.push("### Memory", memorySection(windows));
  out.push("### Server tick", tickSection(windows));
  out.push("### Server process", processSection(windows));
  out.push("### Budgets", budgetSection(windows));
  out.push("### Stability across invocations", stabilitySection(windows));
  const comparison = comparisonSection(
    windows,
    validWindows(scenario, windowsOf(bases.filter((b) => b.scenario === scenario))),
    args.base,
  );
  if (comparison) out.push("### Comparison", comparison);
}
console.log(out.join("\n\n"));
