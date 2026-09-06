// Paints the lion UV atlas into lion.bbmodel textures[0].source (procedural, re-runnable).
// Run: make dc CMD="node resources/entities/lion/generate_texture.mjs"
// Geometry + UV packing live in build_model.mjs — run that first if elements change.
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import zlib from "node:zlib";

const DIR = dirname(fileURLToPath(import.meta.url));
const BB_PATH = join(DIR, "lion.bbmodel");

const SEED = 0x11007;
const PALETTE = {
  body: [200, 143, 60], head: [212, 164, 80], muzzle: [240, 237, 227],
  ear: [70, 45, 28], mane: [154, 106, 56], tail: [200, 143, 60],
  tuft: [74, 48, 32], leg: [190, 135, 58], paw: [120, 86, 40],
};
const MANE_ACCENT = [94, 61, 34];
const EYE = [206, 148, 46];
const PUPIL = [28, 18, 8];
const NOSE = [20, 18, 18];
const GRAIN = { muzzle: 5, ear: 6, mane: 13, tuft: 12, paw: 9 }; // default 10

const mulberry32 = (a) => () => {
  a |= 0; a = (a + 0x6d2b79f5) | 0;
  let t = Math.imul(a ^ (a >>> 15), 1 | a);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);

const bb = JSON.parse(readFileSync(BB_PATH, "utf8"));
const W = bb.resolution.width, H = bb.resolution.height;
const px = new Uint8Array(W * H * 4); // RGBA, starts fully transparent

const set = (x, y, [r, g, b]) => {
  const i = (y * W + x) * 4;
  px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = 255;
};

// per-element value-noise field, bilinearly sampled
const noiseField = (rnd) => {
  const N = 8;
  const g = Array.from({ length: N * N }, () => rnd());
  return (u, v) => {
    const fx = u * (N - 1), fy = v * (N - 1);
    const x0 = Math.min(N - 1, fx | 0), y0 = Math.min(N - 1, fy | 0);
    const x1 = Math.min(N - 1, x0 + 1), y1 = Math.min(N - 1, y0 + 1);
    const tx = fx - x0, ty = fy - y0;
    const a = g[y0 * N + x0], b = g[y0 * N + x1], c = g[y1 * N + x0], d = g[y1 * N + x1];
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
  };
};

const AO = { up: 10, down: -14, north: 2, south: -4, east: -6, west: -6 };

let elemSeed = SEED;
for (const el of bb.elements) {
  const part = el.name.startsWith("mane") ? "mane"
    : el.name.startsWith("leg") ? "leg"
    : el.name.startsWith("paw") ? "paw"
    : el.name.startsWith("ear") ? "ear"
    : el.name === "muzzle" ? "muzzle"
    : el.name.startsWith("tailTuft") ? "tuft"
    : el.name.startsWith("tail") ? "tail"
    : el.name === "head" ? "head" : "body";
  const base = PALETTE[part];
  const grain = GRAIN[part] ?? 10;
  const rnd = mulberry32((elemSeed = (elemSeed * 1664525 + 1013904223) >>> 0));
  const noise = noiseField(rnd);

  for (const [fname, face] of Object.entries(el.faces)) {
    const [x1, y1, x2, y2] = face.uv;
    const fw = x2 - x1, fh = y2 - y1;
    const ao = AO[fname] ?? 0;
    for (let yy = 0; yy < fh; yy++) {
      for (let xx = 0; xx < fw; xx++) {
        const u = fw > 1 ? xx / (fw - 1) : 0;
        const v = fh > 1 ? yy / (fh - 1) : 0;
        let c = base.slice();
        if (part === "mane") {
          const n = noise(u, v);
          const t = n < 0.45 ? 0.75 : n < 0.62 ? 0.35 : 0;
          for (let k = 0; k < 3; k++) c[k] = c[k] * (1 - t) + MANE_ACCENT[k] * t;
        }
        const jitter = (rnd() * 2 - 1) * grain;
        for (let k = 0; k < 3; k++) c[k] = clamp(c[k] + jitter + ao);
        set(x1 + xx, y1 + yy, c);
      }
    }
  }
}

// --- eyes + nose, painted last so nothing overwrites them ---
const head = bb.elements.find((e) => e.name === "head");
{
  const [x1, y1, x2, y2] = head.faces.north.uv; // front of head
  const fw = x2 - x1, fh = y2 - y1;
  const eyeY = y1 + Math.round(fh * 0.28);
  const ew = Math.max(2, Math.round(fw * 0.22));
  const eh = Math.max(2, Math.round(fh * 0.22));
  const inset = Math.max(1, Math.round(fw * 0.12));
  for (const ex of [x1 + inset, x2 - inset - ew]) {
    for (let yy = 0; yy < eh; yy++)
      for (let xx = 0; xx < ew; xx++) set(ex + xx, eyeY + yy, EYE);
    // pupil
    set(ex + (ew >> 1), eyeY + (eh >> 1), PUPIL);
    if (ew > 1) set(ex + (ew >> 1) - 1, eyeY + (eh >> 1), PUPIL);
  }
}
const muzzle = bb.elements.find((e) => e.name === "muzzle");
{
  const [x1, , x2, y2] = muzzle.faces.north.uv;
  const fw = x2 - x1;
  const nw = Math.max(2, Math.round(fw * 0.5));
  const nx = x1 + ((fw - nw) >> 1);
  for (let yy = 1; yy <= 2; yy++)
    for (let xx = 0; xx < nw; xx++) set(nx + xx, y2 - yy, NOSE);
}

// --- close every transparent pixel: iterative neighbor bleed, then hard fallback ---
for (let pass = 0; pass < 80; pass++) {
  let changed = 0;
  const snap = px.slice();
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const i = (y * W + x) * 4;
      if (snap[i + 3] === 255) continue;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        const j = (ny * W + nx) * 4;
        if (snap[j + 3] === 255) {
          px[i] = snap[j]; px[i + 1] = snap[j + 1]; px[i + 2] = snap[j + 2]; px[i + 3] = 255;
          changed++; break;
        }
      }
    }
  }
  if (!changed) break;
}
for (let i = 0; i < px.length; i += 4) {
  if (px[i + 3] !== 255) { px[i] = 60; px[i + 1] = 45; px[i + 2] = 30; px[i + 3] = 255; }
}

// --- minimal RGBA PNG encoder (zlib + CRC32) ---
const crcTable = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
};
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const raw = Buffer.alloc(H * (1 + W * 4));
for (let y = 0; y < H; y++) {
  raw[y * (1 + W * 4)] = 0;
  px.subarray(y * W * 4, (y + 1) * W * 4).forEach((v, k) => { raw[y * (1 + W * 4) + 1 + k] = v; });
}
const png = Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  chunk("IHDR", ihdr),
  chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
  chunk("IEND", Buffer.alloc(0)),
]);

// verify full opacity
for (let i = 3; i < px.length; i += 4) if (px[i] !== 255) throw new Error("transparent pixel remains");

bb.textures[0].source = "data:image/png;base64," + png.toString("base64");
writeFileSync(BB_PATH, JSON.stringify(bb));
console.log(`texture painted: ${W}x${H}, ${png.length} bytes PNG, all pixels opaque`);
