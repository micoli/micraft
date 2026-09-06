#!/usr/bin/env node
// Regenerate cow.bbmodel's embedded texture.
//
// Physical attributes (skill step 1): Holstein dairy cow — white short-hair coat with hard-edged
// black blotches, cream keratin horns, pink udder and muzzle, dark hooves. Amber eyes so they read
// against both the white coat and a black blotch. Procedural: per-pixel grain + a bilinear
// value-noise blotch field, never flat fills.
// Re-run after tweaking SEED / colors / params to rebuild the PNG without touching geometry:
//   make dc CMD="node resources/entities/cow/generate_texture.mjs"
//
// No PNG library in this repo (see app/webApp/ts-src/package.json), so this hand-rolls a minimal
// 8-bit RGBA PNG encoder on node's built-in zlib.

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { deflateSync } from "node:zlib";

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const BBMODEL = join(SCRIPT_DIR, "cow.bbmodel");

const SEED = 20260906;

const COAT_WHITE = [236, 233, 226];
const COAT_BLACK = [34, 31, 33];
const HORN_COLOR = [223, 210, 180];
const UDDER_COLOR = [228, 156, 156];
const SNOUT_COLOR = [206, 158, 152];
const HOOF_COLOR = [43, 38, 40];
const EYE_COLOR = [214, 148, 54]; // amber — reads on white coat and on a black blotch alike

const FUR_JITTER = 9; // short-hair coat — tight grain
const SMOOTH_JITTER = 4; // horns / udder / muzzle
const NOISE_GRID = 8; // coarse value-noise cells per element for the blotch field
const BLOTCH_THRESHOLD = 0.56; // noise value above this => black blotch
const AO_SHADE = 9;
const HOOF_FRACTION = 0.28; // bottom fraction of each leg that is hoof
const EYE_SIZE_FRACTION = 0.2;
const EYE_INSET_FRACTION = 0.12;

const BLOTCHY_ELEMENTS = new Set([
  "body",
  "head",
  "earL",
  "earR",
  "tail",
  "frontLegL",
  "frontLegR",
  "backLegL",
  "backLegR",
]);
const SMOOTH_ELEMENTS = new Set(["hornL", "hornR", "udder", "snout"]);
const LEG_ELEMENTS = new Set(["frontLegL", "frontLegR", "backLegL", "backLegR"]);

// ---- seeded PRNG (mulberry32) — reproducible across runs, unlike Math.random() ----
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = mulberry32(SEED);

function clamp255(v) {
  return Math.max(0, Math.min(255, Math.round(v)));
}

// ---- minimal 8-bit RGBA PNG encoder ----
const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (const byte of buf) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([len, typeBuf, data, crc]);
}

function encodePng(width, height, pixels) {
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter: None
    Buffer.from(pixels.buffer, y * stride, stride).copy(raw, y * (stride + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type: RGBA
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([
    sig,
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

// ---- per-element value-noise field for blotches ----
function makeNoiseField() {
  const grid = [];
  for (let i = 0; i <= NOISE_GRID; i++) {
    const row = [];
    for (let j = 0; j <= NOISE_GRID; j++) row.push(rng());
    grid.push(row);
  }
  return (u, v) => {
    // u,v in [0,1) -> bilinear sample
    const gx = u * NOISE_GRID;
    const gy = v * NOISE_GRID;
    const x0 = Math.min(NOISE_GRID - 1, Math.floor(gx));
    const y0 = Math.min(NOISE_GRID - 1, Math.floor(gy));
    const fx = gx - x0;
    const fy = gy - y0;
    const a = grid[y0][x0];
    const b = grid[y0][x0 + 1];
    const c = grid[y0 + 1][x0];
    const d = grid[y0 + 1][x0 + 1];
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy;
  };
}

// ---- texture generation ----
const model = JSON.parse(readFileSync(BBMODEL, "utf8"));
const { width, height } = model.resolution;
const canvas = new Uint8ClampedArray(width * height * 4);
// Ground the whole atlas in opaque coat-white first: unused packer space then reads as coat,
// not as a black hole, and the gutter-bleed below only has to cover 1-2px seams.
for (let i = 0; i < canvas.length; i += 4) {
  canvas[i] = COAT_WHITE[0];
  canvas[i + 1] = COAT_WHITE[1];
  canvas[i + 2] = COAT_WHITE[2];
  canvas[i + 3] = 255;
}
const painted = new Uint8Array(width * height);

for (const element of model.elements) {
  const blotchy = BLOTCHY_ELEMENTS.has(element.name);
  const smooth = SMOOTH_ELEMENTS.has(element.name);
  const isLeg = LEG_ELEMENTS.has(element.name);
  const noise = blotchy ? makeNoiseField() : null;

  let baseColor = COAT_WHITE;
  if (element.name === "hornL" || element.name === "hornR") baseColor = HORN_COLOR;
  else if (element.name === "udder") baseColor = UDDER_COLOR;
  else if (element.name === "snout") baseColor = SNOUT_COLOR;

  for (const [faceName, face] of Object.entries(element.faces)) {
    const [ux1, uy1, ux2, uy2] = face.uv;
    const x1 = Math.min(ux1, ux2);
    const x2 = Math.max(ux1, ux2);
    const y1 = Math.min(uy1, uy2);
    const y2 = Math.max(uy1, uy2);
    const fw = Math.max(1, x2 - x1);
    const fh = Math.max(1, y2 - y1);
    const isTopBottom = faceName === "up" || faceName === "down";
    const aoShift = faceName === "up" ? AO_SHADE : faceName === "down" ? -AO_SHADE : -AO_SHADE / 3;

    for (let x = x1; x < x2; x++) {
      for (let y = y1; y < y2; y++) {
        const u = (x - x1) / fw;
        const v = (y - y1) / fh;
        let [r, g, b] = baseColor;

        if (blotchy) {
          const n = noise(u, v);
          if (n > BLOTCH_THRESHOLD) [r, g, b] = COAT_BLACK;
        }

        // hooves: dark band at the bottom of side faces + whole down face
        if (isLeg && (faceName === "down" || (!isTopBottom && v > 1 - HOOF_FRACTION))) {
          [r, g, b] = HOOF_COLOR;
        }

        const jitter = (rng() - 0.5) * (smooth ? SMOOTH_JITTER : FUR_JITTER);
        r = clamp255(r + jitter + aoShift);
        g = clamp255(g + jitter + aoShift);
        b = clamp255(b + jitter + aoShift);

        const di = (y * width + x) * 4;
        canvas[di] = r;
        canvas[di + 1] = g;
        canvas[di + 2] = b;
        canvas[di + 3] = 255;
        painted[y * width + x] = 1;
      }
    }
  }
}

// Eyes — painted last, flat, on the head's front (north) UV rect so nothing overwrites them.
const head = model.elements.find((e) => e.name === "head");
const [hx1, hy1, hx2, hy2] = head.faces.north.uv;
const faceX1 = Math.min(hx1, hx2);
const faceY1 = Math.min(hy1, hy2);
const faceW = Math.abs(hx2 - hx1);
const faceH = Math.abs(hy2 - hy1);
const eyeW = Math.max(1, Math.round(faceW * EYE_SIZE_FRACTION));
const eyeH = Math.max(1, Math.round(faceH * EYE_SIZE_FRACTION));
const inset = Math.round(faceW * EYE_INSET_FRACTION);
const eyeY = faceY1 + Math.round(faceH * 0.32);
for (const eyeX of [faceX1 + inset, faceX1 + faceW - inset - eyeW]) {
  for (let x = eyeX; x < eyeX + eyeW; x++) {
    for (let y = eyeY; y < eyeY + eyeH; y++) {
      const di = (y * width + x) * 4;
      canvas[di] = EYE_COLOR[0];
      canvas[di + 1] = EYE_COLOR[1];
      canvas[di + 2] = EYE_COLOR[2];
      canvas[di + 3] = 255;
    }
  }
}

// Gutter bleed — spread painted face pixels a few px outward so bilinear filtering at UV
// edges samples coat colour, not the white ground. The canvas is already fully opaque, so
// this only smooths seams; it never has to cross the large blank packer areas.
const NEIGHBOURS = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
];
for (let pass = 0; pass < 3; pass++) {
  const paintedSnap = Uint8Array.from(painted);
  const colorSnap = Uint8ClampedArray.from(canvas);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (paintedSnap[y * width + x]) continue;
      for (const [dx, dy] of NEIGHBOURS) {
        const nx = x + dx;
        const ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
        if (!paintedSnap[ny * width + nx]) continue;
        const di = (y * width + x) * 4;
        const ni = (ny * width + nx) * 4;
        canvas[di] = colorSnap[ni];
        canvas[di + 1] = colorSnap[ni + 1];
        canvas[di + 2] = colorSnap[ni + 2];
        painted[y * width + x] = 1;
        break;
      }
    }
  }
}

const png = encodePng(width, height, canvas);
model.textures[0].source = "data:image/png;base64," + png.toString("base64");
writeFileSync(BBMODEL, JSON.stringify(model));
console.log("wrote", BBMODEL, "texture", `${width}x${height}`);
