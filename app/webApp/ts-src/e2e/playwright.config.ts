import { defineConfig } from "@playwright/test";
import { resolve } from "node:path";

const PORT = process.env.E2E_PORT ?? "8091";
// playwright loads this config with cwd = app/webApp/ts-src (see package.json scripts)  → up 3
const REPO_ROOT = resolve(process.cwd(), "../../..");

/**
 * One Ktor server (booted with MICRAFT_E2E=1) hosts every test's world: each test gets its own
 * isolated GameWorld and account, keyed by `testInfo.testId` (+ retry) via ?gameSession= — see
 * `accountFor` in helpers/connectClient.ts. So tests never share terrain, drops or a player set,
 * whether run in parallel or sequentially on the same worker.
 */
export default defineConfig({
  testDir: ".",
  testMatch: "**/*.spec.ts",
  fullyParallel: true,
  workers: process.env.CI ? 2 : 4,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    // Babylon needs a GL context in headless Chromium.
    // launchOptions: { args: ["--use-gl=swiftshader", "--enable-unsafe-swiftshader"] },
    headless: !!process.env.CI,
    // CI: software GL (no GPU). Local: real GPU + no background throttling so the WebGL canvas
    // updates in real-time in the headed browser window.
    launchOptions: {
      args: [
        ...(process.env.CI ? ["--use-gl=swiftshader", "--enable-unsafe-swiftshader"] : []),
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-background-timer-throttling",
      ],
    },
  },
  // Set E2E_NO_SERVER=1 to skip managing the server entirely (already running / hosted elsewhere).
  webServer: process.env.E2E_NO_SERVER
    ? undefined
    : {
        // `make e2e` builds the client first, so here we only boot the server. Locally you can also
        // run it yourself (`make e2e-server` or `pitchfork start e2e-server`) and this block reuses it.
        command: "./gradlew :server:runE2eServer --console=plain",
        cwd: REPO_ROOT,
        url: `http://localhost:${PORT}/api/auth/config`,
        reuseExistingServer: !process.env.CI,
        timeout: 180_000,
        stdout: "pipe",
        stderr: "pipe",
      },
});
