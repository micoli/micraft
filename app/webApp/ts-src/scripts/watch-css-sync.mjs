#!/usr/bin/env node
// Watches the main built CSS file and copies it to map/admin paths.
// Replaces 2 separate tailwind watch processes with a near-instant copy.
// Assumes main.css is identical content for all 3 targets (same tailwind input).
import { watch, copyFileSync, existsSync, mkdirSync } from "fs";
import { dirname } from "path";

const src = process.env.MC_OUT_CSS;
const targets = [process.env.MC_OUT_MAP_CSS, process.env.MC_OUT_ADMIN_CSS].filter(Boolean);

if (!src) {
  console.error("[css-sync] MC_OUT_CSS must be set");
  process.exit(1);
}

function ensureDir(file) {
  const dir = dirname(file);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
}

function sync() {
  if (!existsSync(src)) return;
  for (const t of targets) {
    ensureDir(t);
    copyFileSync(src, t);
  }
  const ts = new Date().toTimeString().slice(0, 8);
  console.log(`[css-sync] synced → ${targets.map((t) => t.split("/").slice(-1)[0]).join(", ")} (${ts})`);
}

// Initial sync (main.css may already exist from a prior build)
sync();

// Watch for changes with a small debounce to avoid double-fire on atomic writes
let timer = null;

function startWatch() {
  watch(src, () => {
    clearTimeout(timer);
    timer = setTimeout(sync, 100);
  });
  console.log(`[css-sync] watching ${src.split("/").slice(-3).join("/")} …`);
}

if (existsSync(src)) {
  startWatch();
} else {
  console.log(`[css-sync] waiting for ${src.split("/").slice(-1)[0]} to appear …`);
  const poller = setInterval(() => {
    if (existsSync(src)) {
      clearInterval(poller);
      sync();
      startWatch();
    }
  }, 500);
}
