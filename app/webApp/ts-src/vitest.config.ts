import { defineConfig, configDefaults } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    // Playwright specs under e2e/ are run by `npm run test:e2e`, not vitest.
    exclude: [...configDefaults.exclude, "e2e/**"],
  },
});
