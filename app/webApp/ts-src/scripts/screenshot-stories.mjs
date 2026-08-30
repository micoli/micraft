// Renders tagged Storybook stories in headless Chromium and writes transparent PNG
// snapshots into docs/assets/stories/, plus .storybook/stories-manifest.json.
//
// A docs page opts in with the story id shown in Storybook's URL
// (`?path=/story/<id>`):
//
//   {{ story "story/game-layout-playerstatusbar--caster" }}
//   {{ story "game-layout-playerstatusbar--caster" caption="…" }}
//
// The MkDocs hook (scripts/docs/hooks.py) turns each tag into an <img> pointing at
// the committed PNG — the docs CI never runs a browser, so these are regenerated
// here (`make docs-screenshots`) and committed.
//
//   node scripts/screenshot-stories.mjs           regenerate committed assets
//   node scripts/screenshot-stories.mjs --all     also snapshot every story
//   node scripts/screenshot-stories.mjs --check    fail if committed assets are stale
//
// Run with cwd = app/webApp/ts-src.

import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdir, mkdtemp, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import process from "node:process";
import { chromium } from "playwright";
import waitOn from "wait-on";

const PORT = Number(process.env.SB_SCREENSHOT_PORT ?? 9010);
const SB_URL = `http://127.0.0.1:${PORT}`;
const VIEWPORT = { width: 1280, height: 900 };

const TS_SRC = process.cwd();
const REPO_ROOT = path.resolve(TS_SRC, "../../..");
const DOCS_DIR = path.join(REPO_ROOT, "docs");
const ASSETS_DIR = path.join(DOCS_DIR, "assets/stories");
const MANIFEST_PATH = path.join(TS_SRC, ".storybook/stories-manifest.json");

const TAG_SCAN_RE = /\{\{\s*story\s+"([^"]+)"/g;

// Accepts the value copied from Storybook's URL (`story/<id>`, `/story/<id>`) or a
// bare story id.
function normId(raw) {
  return raw
    .trim()
    .replace(/^\/?story\//, "")
    .replace(/^\/+/, "");
}

const args = new Set(process.argv.slice(2));
const CHECK = args.has("--check");
const ALL = args.has("--all");

function log(...m) {
  console.log("[screenshot-stories]", ...m);
}

function die(msg) {
  console.error(`[screenshot-stories] ${msg}`);
  process.exit(1);
}

async function collectTags() {
  const ids = new Set();
  const files = await readdir(DOCS_DIR, { recursive: true });
  for (const rel of files) {
    if (!rel.endsWith(".md")) continue;
    const text = await readFile(path.join(DOCS_DIR, rel), "utf8");
    for (const m of text.matchAll(TAG_SCAN_RE)) ids.add(normId(m[1]));
  }
  return [...ids];
}

// { "<story-id>": { title, name, source } } — keyed by the id used in the tags.
function buildManifest(entries) {
  const manifest = {};
  for (const id of Object.keys(entries).sort()) {
    const e = entries[id];
    if (e.type && e.type !== "story") continue;
    manifest[id] = {
      title: e.title,
      name: e.name,
      source: (e.importPath ?? "").replace(/^\.\//, ""),
    };
  }
  return manifest;
}

function resolveTargets(ids, manifest) {
  const targets = new Map(); // id -> label
  for (const id of ids) {
    const entry = manifest[id];
    if (!entry) {
      const stem = id.split("--")[0];
      const near = Object.keys(manifest)
        .filter((k) => k.startsWith(stem))
        .slice(0, 6);
      die(
        `unknown story id ${JSON.stringify(id)}` +
          (near.length ? ` — did you mean: ${near.join(", ")}` : " (see Storybook URL ?path=/story/<id>)"),
      );
    }
    targets.set(id, `${entry.title} / ${entry.name}`);
  }
  return targets;
}

async function startStorybook() {
  log(`starting storybook on :${PORT} …`);
  const bin = path.join(TS_SRC, "node_modules/.bin/storybook");
  const child = spawn(bin, ["dev", "-p", String(PORT), "--no-open", "--quiet", "--ci"], {
    cwd: TS_SRC,
    stdio: "ignore",
    env: { ...process.env, NODE_ENV: "production" },
  });
  child.on("exit", (code) => {
    if (code && code !== 0 && !child.killed) die(`storybook exited early (code ${code})`);
  });
  await waitOn({ resources: [`${SB_URL}/index.json`], timeout: 180_000, interval: 500 });
  log("storybook ready");
  return child;
}

async function fetchIndex() {
  const res = await fetch(`${SB_URL}/index.json`);
  if (!res.ok) die(`GET /index.json → ${res.status}`);
  const json = await res.json();
  return json.entries ?? json.stories ?? {};
}

async function shoot(targets, outDir) {
  await mkdir(outDir, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 2,
    reducedMotion: "reduce",
  });
  const written = [];
  try {
    for (const [id, label] of targets) {
      const page = await context.newPage();
      try {
        const url = `${SB_URL}/iframe.html?id=${encodeURIComponent(id)}&viewMode=story&globals=backgrounds.value:transparent`;
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 60_000 });
        await page.waitForSelector("#storybook-root", { state: "attached", timeout: 60_000 });
        /* eslint-disable no-undef -- runs in the page, not in Node */
        await page.waitForFunction(
          () => {
            if (document.querySelector(".sb-show-errordisplay")) return true;
            const r = document.querySelector("#storybook-root");
            // Radix dialogs portal out of #storybook-root — accept a portalled node too.
            const portal = document.querySelector("body > div:not(#storybook-root):not(#storybook-docs)");
            return (r && r.children.length > 0) || !!portal;
          },
          { timeout: 60_000 },
        );
        await page.waitForLoadState("networkidle").catch(() => {});
        await page.evaluate(() => document.fonts?.ready).catch(() => {});
        await page.waitForTimeout(1200); // let an async play() run (interactions, and it may throw)
        const errText = await page.evaluate(() => {
          if (document.querySelector(".sb-show-errordisplay")) return "render error";
          const t = (document.querySelector("#storybook-root")?.innerText || "") + (document.body.innerText || "");
          return /Unable to perform pointer interaction|component failed to render properly|Story is missing|storybook-root not found/i.test(
            t,
          )
            ? t.replace(/\s+/g, " ").slice(0, 160)
            : "";
        });
        if (errText) die(`story ${id} errored during render/play — fix the story: ${errText}`);
        /* eslint-enable no-undef */
        await page.addStyleTag({
          content: "html,body,.sb-show-main,#storybook-root{background:transparent !important}",
        });
        // Give width to full-bleed HUD bars (flex-1 / w-full) that otherwise
        // collapse in the intrinsic-width `layout: centered` wrapper.
        /* eslint-disable no-undef -- runs in the page, not in Node */
        await page.evaluate(() => {
          const r = document.querySelector("#storybook-root");
          if (!r) return;
          const starved = [...r.querySelectorAll("*")].some((e) => {
            const s = getComputedStyle(e);
            return parseFloat(s.flexGrow) > 0 && e.getBoundingClientRect().width < 120;
          });
          if (starved) r.style.width = "440px";
        });
        /* eslint-enable no-undef */
        await page.waitForTimeout(200);
        const file = path.join(outDir, `${id}.png`);

        // #storybook-root collapses (position:fixed HUD) or fills the viewport
        // (fullscreen layout, portalled dialog overlay) — clip to the union of the
        // actually-painted content instead.
        const clip = await contentClip(page);
        await page.screenshot({ path: file, clip, omitBackground: true, animations: "disabled" });
        written.push(`${id}.png`);
        log(`✓ ${label}  →  ${path.relative(REPO_ROOT, file)}`);
      } finally {
        await page.close();
      }
    }
  } finally {
    await browser.close();
  }
  return written;
}

// Bounding box of everything actually painted (a dialog frame, a portalled menu, a
// fixed HUD panel), ignoring full-viewport transparent overlays and layout padding.
async function contentClip(page) {
  /* eslint-disable no-undef -- runs in the page, not in Node */
  const box = await page.evaluate(() => {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const roots = [
      document.querySelector("#storybook-root"),
      ...document.querySelectorAll("body > div:not(#storybook-root):not(#storybook-docs)"),
    ].filter(Boolean);

    const paints = (el) => {
      const s = getComputedStyle(el);
      if (s.visibility === "hidden" || s.display === "none" || s.opacity === "0") return false;
      const bg = s.backgroundColor !== "rgba(0, 0, 0, 0)" && s.backgroundColor !== "transparent";
      const bordered = parseFloat(s.borderWidth) > 0 || s.boxShadow !== "none";
      const media = ["IMG", "SVG", "CANVAS", "VIDEO"].includes(el.tagName);
      const text = el.childNodes && [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim());
      return bg || bordered || media || text;
    };

    const vArea = vw * vh;
    let x0 = Infinity;
    let y0 = Infinity;
    let x1 = -Infinity;
    let y1 = -Infinity;
    for (const root of roots) {
      for (const el of [root, ...root.querySelectorAll("*")]) {
        const r = el.getBoundingClientRect();
        if (r.width < 1 || r.height < 1) continue;
        // ignore layout/overlay containers: near-viewport size, or a large mostly
        // empty box that would only pad the frame with transparent margin.
        if (r.width >= vw - 2 && r.height >= vh - 2) continue;
        if (r.width * r.height > vArea * 0.4) continue;
        if (!paints(el)) continue;
        x0 = Math.min(x0, r.left);
        y0 = Math.min(y0, r.top);
        x1 = Math.max(x1, r.right);
        y1 = Math.max(y1, r.bottom);
      }
    }
    if (!isFinite(x0)) return { x: 0, y: 0, width: vw, height: vh };
    const pad = 1;
    x0 = Math.max(0, Math.floor(x0 - pad));
    y0 = Math.max(0, Math.floor(y0 - pad));
    x1 = Math.min(vw, Math.ceil(x1 + pad));
    y1 = Math.min(vh, Math.ceil(y1 + pad));
    return { x: x0, y: y0, width: x1 - x0, height: y1 - y0 };
  });
  /* eslint-enable no-undef */
  return box;
}

async function readIfExists(p) {
  try {
    return await readFile(p);
  } catch {
    return null;
  }
}

async function main() {
  const tags = ALL ? [] : await collectTags();
  if (!ALL && tags.length === 0) {
    log('no {{ story "…" }} tags found under docs/ — nothing to do');
  }

  const sb = await startStorybook();
  try {
    const entries = await fetchIndex();
    const manifest = buildManifest(entries);
    const manifestJson = JSON.stringify(manifest, null, 2) + "\n";

    const targets = ALL
      ? new Map(Object.keys(manifest).map((id) => [id, `${manifest[id].title} / ${manifest[id].name}`]))
      : resolveTargets(tags, manifest);

    if (CHECK) {
      // Re-render every tagged story (shoot() aborts on a render/play error) but
      // don't pixel-compare — PNG output isn't reproducible across OS / font
      // stacks. Guards: manifest fresh, no orphan, every tag resolves & renders,
      // committed PNG present.
      const problems = [];
      if ((await readIfExists(MANIFEST_PATH))?.toString() !== manifestJson) {
        problems.push(`${path.relative(REPO_ROOT, MANIFEST_PATH)} is stale`);
      }
      const committed = new Set(
        (existsSync(ASSETS_DIR) ? await readdir(ASSETS_DIR) : []).filter((f) => f.endsWith(".png")),
      );
      const tmp = await mkdtemp(path.join(tmpdir(), "sb-shots-"));
      try {
        await shoot(targets, tmp);
      } finally {
        await rm(tmp, { recursive: true, force: true });
      }
      for (const [id, label] of targets) {
        const png = await readIfExists(path.join(ASSETS_DIR, `${id}.png`));
        if (!png || png.length < 256) problems.push(`missing/empty docs/assets/stories/${id}.png for ${label}`);
        committed.delete(`${id}.png`);
      }
      for (const orphan of committed) {
        problems.push(`orphan docs/assets/stories/${orphan} — no tag references it`);
      }
      if (problems.length)
        die(`story screenshots out of date (run \`make docs-screenshots\`):\n  - ${problems.join("\n  - ")}`);
      log(`story assets OK (${targets.size} tagged, manifest current)`);
      return;
    }

    await mkdir(ASSETS_DIR, { recursive: true });
    await writeFile(MANIFEST_PATH, manifestJson);
    const written = await shoot(targets, ASSETS_DIR);

    // prune orphans
    const keep = new Set(written);
    for (const f of existsSync(ASSETS_DIR) ? await readdir(ASSETS_DIR) : []) {
      if (f.endsWith(".png") && !keep.has(f)) {
        await rm(path.join(ASSETS_DIR, f));
        log(`removed orphan ${f}`);
      }
    }
    log(`done — ${written.length} screenshot(s), manifest updated`);
  } finally {
    sb.kill("SIGTERM");
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
