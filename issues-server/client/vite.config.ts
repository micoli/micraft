import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "node:path";

export default defineConfig({
  root: import.meta.dirname,
  plugins: [react()],
  server: {
    port: Number(process.env.ISSUES_DEV_PORT ?? 4381),
    proxy: { "/api": `http://127.0.0.1:${process.env.ISSUES_PORT ?? 4380}` },
  },
  build: {
    outDir: resolve(import.meta.dirname, "..", process.env.ISSUES_STATIC_DIR ?? "client/dist"),
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000,
  },
});
