// Paints resources/entities/pig/pig.bbmodel embedded texture from its per-face UV rects.
// Re-runnable: tweak the constants below, re-run, geometry untouched. Run:
//   make dc CMD="node resources/entities/pig/generate_texture.mjs"
import { readFileSync, writeFileSync } from "node:fs";
import zlib from "node:zlib";

const SEED = 0x91607;

// --- palette ---------------------------------------------------------------
const PINK = [230, 169, 180]; // body / head / legs / tail
const PINK_DARK = [140, 90, 110]; // ears inner, snout
const HOOF = [58, 58, 58]; // leg tips
const EYE = [45, 45, 45];

const GRAIN = 7; // per-channel jitter (smooth skin -> tight)
const MOTTLE = 10; // subtle darker blotching amplitude

// --- seeded PRNG ----------------------------------------------------------
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const modelUrl = new URL("./pig.bbmodel", import.meta.url);
const model = JSON.parse(readFileSync(modelUrl));
const W = model.resolution.width;
const H = model.resolution.height;
const buf = new Uint8Array(W * H * 4); // RGBA, starts fully transparent

function set(x, y, [r, g, b]) {
  const i = (y * W + x) * 4;
  buf[i] = r;
  buf[i + 1] = g;
  buf[i + 2] = b;
  buf[i + 3] = 255;
}
const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);

function partColor(name, face) {
  if (name === "snout" || name === "ear_left" || name === "ear_right") return PINK_DARK;
  return PINK;
}
const isLeg = (n) => n.startsWith("leg_");

// value-noise grid per element for mottling
function noiseField(rnd, gw, gh) {
  const g = [];
  for (let i = 0; i < gw * gh; i++) g.push(rnd());
  return (u, v) => {
    const fx = u * (gw - 1);
    const fy = v * (gh - 1);
    const x0 = Math.floor(fx);
    const y0 = Math.floor(fy);
    const x1 = Math.min(x0 + 1, gw - 1);
    const y1 = Math.min(y0 + 1, gh - 1);
    const tx = fx - x0;
    const ty = fy - y0;
    const a = g[y0 * gw + x0];
    const b = g[y0 * gw + x1];
    const c = g[y1 * gw + x0];
    const d = g[y1 * gw + x1];
    return (
      a * (1 - tx) * (1 - ty) +
      b * tx * (1 - ty) +
      c * (1 - tx) * ty +
      d * tx * ty
    );
  };
}

const rnd = mulberry32(SEED);
for (const el of model.elements) {
  for (const [faceName, face] of Object.entries(el.faces)) {
    const [x1, y1, x2, y2] = face.uv;
    const fw = x2 - x1;
    const fh = y2 - y1;
    const base = partColor(el.name, faceName);
    const shade = faceName === "up" ? 8 : faceName === "down" ? -12 : 0;
    const nf = noiseField(rnd, 4, 4);
    for (let yy = 0; yy < fh; yy++) {
      for (let xx = 0; xx < fw; xx++) {
        let col = base;
        // leg tips: hoof on the down face + lowest 2 px of side faces
        if (isLeg(el.name)) {
          const nearBottom = faceName === "down" || (faceName !== "up" && yy >= fh - 2);
          if (nearBottom) col = HOOF;
        }
        const m = (nf(xx / fw, yy / fh) - 0.5) * 2 * MOTTLE;
        const px = [
          clamp(col[0] + shade - m + (rnd() - 0.5) * 2 * GRAIN),
          clamp(col[1] + shade - m + (rnd() - 0.5) * 2 * GRAIN),
          clamp(col[2] + shade - m + (rnd() - 0.5) * 2 * GRAIN),
        ];
        set(x1 + xx, y1 + yy, px);
      }
    }
  }
}

// --- eyes: painted last, head front face (north = -Z) --------------------
const head = model.elements.find((e) => e.name === "head");
{
  const [x1, y1, x2, y2] = head.faces.north.uv;
  const fw = x2 - x1;
  const fh = y2 - y1;
  const ew = Math.max(1, Math.round(fw * 0.16));
  const eh = Math.max(1, Math.round(fh * 0.16));
  const ey = y1 + Math.round(fh * 0.35);
  const exL = x1 + Math.round(fw * 0.16);
  const exR = x2 - Math.round(fw * 0.16) - ew;
  for (let yy = 0; yy < eh; yy++)
    for (let xx = 0; xx < ew; xx++) {
      set(exL + xx, ey + yy, EYE);
      set(exR + xx, ey + yy, EYE);
    }
}

// --- fill transparent gutters: iterative neighbour bleed then fallback ---
function alphaAt(x, y) {
  return buf[(y * W + x) * 4 + 3];
}
for (let pass = 0; pass < Math.max(W, H); pass++) {
  let changed = false;
  for (let y = 0; y < H; y++)
    for (let x = 0; x < W; x++) {
      if (alphaAt(x, y) === 255) continue;
      const nb = [
        [x - 1, y],
        [x + 1, y],
        [x, y - 1],
        [x, y + 1],
      ].find(([nx, ny]) => nx >= 0 && ny >= 0 && nx < W && ny < H && alphaAt(nx, ny) === 255);
      if (nb) {
        const j = (nb[1] * W + nb[0]) * 4;
        set(x, y, [buf[j], buf[j + 1], buf[j + 2]]);
        changed = true;
      }
    }
  if (!changed) break;
}
for (let i = 0; i < W * H; i++)
  if (buf[i * 4 + 3] !== 255) set(i % W, (i / W) | 0, PINK);

// verify opacity
for (let i = 0; i < W * H; i++)
  if (buf[i * 4 + 3] !== 255) throw new Error("transparent pixel remains");

// --- minimal RGBA PNG encoder -------------------------------------------
const crcTable = (() => {
  const t = [];
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(bytes) {
  let c = 0xffffffff;
  for (const b of bytes) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const tb = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([tb, data])));
  return Buffer.concat([len, tb, data, crc]);
}
const raw = Buffer.alloc((W * 4 + 1) * H);
for (let y = 0; y < H; y++) {
  raw[y * (W * 4 + 1)] = 0;
  for (let x = 0; x < W * 4; x++) raw[y * (W * 4 + 1) + 1 + x] = buf[y * W * 4 + x];
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0);
ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8;
ihdr[9] = 6;
const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk("IHDR", ihdr),
  chunk("IDAT", zlib.deflateSync(raw)),
  chunk("IEND", Buffer.alloc(0)),
]);

model.textures[0].source = "data:image/png;base64," + png.toString("base64");
writeFileSync(modelUrl, JSON.stringify(model));

// upscaled nearest-neighbour preview for eyeballing — only when PIG_PREVIEW=1
const S = 12;
if (process.env.PIG_PREVIEW) {
const bigBuf = Buffer.alloc(W * S * H * S * 4);
for (let y = 0; y < H * S; y++)
  for (let x = 0; x < W * S; x++) {
    const src = (((y / S) | 0) * W + ((x / S) | 0)) * 4;
    bigBuf.set(buf.subarray(src, src + 4), (y * W * S + x) * 4);
  }
const bw = W * S;
const bh = H * S;
const braw = Buffer.alloc((bw * 4 + 1) * bh);
for (let y = 0; y < bh; y++) for (let x = 0; x < bw * 4; x++) braw[y * (bw * 4 + 1) + 1 + x] = bigBuf[y * bw * 4 + x];
const bihdr = Buffer.alloc(13);
bihdr.writeUInt32BE(bw, 0);
bihdr.writeUInt32BE(bh, 4);
bihdr[8] = 8;
bihdr[9] = 6;
const bigPng = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk("IHDR", bihdr),
  chunk("IDAT", zlib.deflateSync(braw)),
  chunk("IEND", Buffer.alloc(0)),
]);
  writeFileSync(new URL("./pig_texture_preview_x12.png", import.meta.url), bigPng);
}
console.log(`painted ${W}x${H} texture into pig.bbmodel`);
