import { defineConfig } from "@playwright/test";

const PORT = process.env.E2E_PORT ?? "8091";

/**
 * One Ktor server (booted with MICRAFT_E2E=1) hosts every test's world: each spec gets an
 * isolated GameWorld keyed by its Playwright parallelIndex via ?gameSession=. So workers can run
 * in parallel against the single server without sharing terrain or a player set.
 */
export default defineConfig({
  testDir: ".",
  testMatch: "**/*.spec.ts",
  fullyParallel: true,
  workers: process.env.CI ? 4 : 4,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    // Babylon needs a GL context in headless Chromium.
    launchOptions: { args: ["--use-gl=swiftshader", "--enable-unsafe-swiftshader"] },
  },
  webServer: {
    // `make e2e` builds the client first; here we only need the server. Run from the repo root.
    command: "cd ../../.. && ./gradlew :server:runE2eServer --console=plain",
    url: `http://localhost:${PORT}/api/auth/config`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    stdout: "pipe",
    stderr: "pipe",
  },
});
