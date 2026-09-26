import type { Page } from "@playwright/test";
import { execSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { cpus, platform, release, totalmem } from "node:os";
import { resolve } from "node:path";
import type { WindowResult } from "./perfSession";
import { adminFetch } from "./perfSession";

const REPO_ROOT = resolve(process.cwd(), "../../..");
const RESULTS_DIR = resolve(REPO_ROOT, ".scratch/perf-baseline/results");

interface ServerPhase {
  name: string;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
  maxMs: number;
}

interface ServerSnapshot {
  tick: ServerPhase[];
  process: { heapMaxBytes: number; cpuLoadAvg: number; gcTimeMs: number; networkBytesOut: number };
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

/** The headline figures, each the median across measured repetitions. */
function summarize(runs: WindowResult[]) {
  const pick = (f: (r: WindowResult) => number) => median(runs.map(f));
  const total = (r: WindowResult) => (r.server as ServerSnapshot).tick.find((p) => p.name === "total");
  const proc = (r: WindowResult) => (r.server as ServerSnapshot).process;
  return {
    clientFps: pick((r) => (r.client.frames * 1000) / r.client.durationMs),
    clientFrameP50Ms: pick((r) => r.client.frameMs.p50),
    clientFrameP95Ms: pick((r) => r.client.frameMs.p95),
    clientFrameP99Ms: pick((r) => r.client.frameMs.p99),
    clientGpuP95Ms: pick((r) => r.client.gpuFrameMs?.p95 ?? 0),
    clientDrawCallsAvg: pick((r) => r.client.drawCalls.avg),
    clientJsHeapBytes: pick((r) => r.client.memory.jsHeapUsedBytes ?? 0),
    clientGpuBufferBytes: pick((r) => r.client.memory.gpuBufferBytes ?? 0),
    serverTickP50Ms: pick((r) => total(r)?.p50Ms ?? 0),
    serverTickP95Ms: pick((r) => total(r)?.p95Ms ?? 0),
    serverTickP99Ms: pick((r) => total(r)?.p99Ms ?? 0),
    serverHeapMaxBytes: pick((r) => proc(r).heapMaxBytes),
    serverCpuLoadAvg: pick((r) => proc(r).cpuLoadAvg),
    serverGcTimeMs: pick((r) => proc(r).gcTimeMs),
    networkBytesOut: pick((r) => proc(r).networkBytesOut),
  };
}

async function environment(page: Page) {
  const gpu = await page.evaluate(() => {
    const gl = document.createElement("canvas").getContext("webgl2");
    if (!gl) return "unknown";
    const info = gl.getExtension("WEBGL_debug_renderer_info");
    return String(gl.getParameter(info ? info.UNMASKED_RENDERER_WEBGL : gl.RENDERER));
  });
  const authConfig = (await (await adminFetch("/api/auth/config")).json()) as { messageEncoder?: string };
  return {
    gitSha: execSync("git rev-parse --short HEAD", { cwd: REPO_ROOT }).toString().trim(),
    gitDirty: execSync("git status --porcelain", { cwd: REPO_ROOT }).toString().trim().length > 0,
    date: new Date().toISOString(),
    machine: {
      cpu: cpus()[0]?.model,
      cores: cpus().length,
      memBytes: totalmem(),
      os: `${platform()} ${release()}`,
      gpu,
    },
    config: { messageEncoder: authConfig.messageEncoder, viewport: page.viewportSize() },
  };
}

export async function writeResult(page: Page, scenario: string, warmup: WindowResult, runs: WindowResult[]) {
  const env = await environment(page);
  const result = { scenario, ...env, summary: summarize(runs), runs, warmup };
  mkdirSync(RESULTS_DIR, { recursive: true });
  const file = resolve(RESULTS_DIR, `${env.date.slice(0, 10)}-${env.gitSha}-${scenario}.json`);
  writeFileSync(file, JSON.stringify(result, null, 2));
  console.log(`[perf] ${scenario} → ${file}\n${JSON.stringify(result.summary, null, 2)}`);
}
