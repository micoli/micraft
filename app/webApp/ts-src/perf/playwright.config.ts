import { defineConfig } from "@playwright/test";
import { resolve } from "node:path";

export const PERF_PORT = process.env.PERF_PORT ?? "8092";
// playwright loads this config with cwd = app/webApp/ts-src → up 3
const REPO_ROOT = resolve(process.cwd(), "../../..");

/**
 * Perf scenarios run one at a time in a headed browser on the real GPU, against the fixed-seed
 * perf server (`make perf-server`, see .scratch/perf-baseline/spec.md). Parallel runs would
 * measure each other.
 */
export default defineConfig({
  testDir: ".",
  // `make perf-heap` / `make perf-cpu` run the profilers on their own: they would skew the timed scenarios.
  testMatch: process.env.PERF_PROFILE ? `**/*.${process.env.PERF_PROFILE}.ts` : "**/*.perf.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 20 * 60_000,
  reporter: [["list"]],
  use: {
    baseURL: `http://localhost:${PERF_PORT}`,
    headless: false,
    viewport: { width: 1600, height: 900 },
    launchOptions: {
      args: [
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-background-timer-throttling",
        "--enable-precise-memory-info",
      ],
    },
  },
  webServer: process.env.PERF_NO_SERVER
    ? undefined
    : {
        command: "./gradlew :server:runPerfServer --console=plain",
        cwd: REPO_ROOT,
        url: `http://localhost:${PERF_PORT}/api/auth/config`,
        reuseExistingServer: true,
        timeout: 300_000,
        stdout: "pipe",
        stderr: "pipe",
      },
});
