// Builds fox.bbmodel geometry (deterministic) and paints its texture procedurally.
// Re-run (node generate_texture.mjs) to regenerate; bump SEED for a same-style variant.
import { writeFileSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, "fox.bbmodel");

const SEED = 20260911;
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = mulberry32(SEED);

// ---- Colors -----------------------------------------------------------------
const FUR_ORANGE = [214, 96, 40];
const FUR_ORANGE_DARK = [176, 74, 28];
const BELLY_WHITE = [244, 236, 222];
const SOCK_BLACK = [30, 26, 24];
const EYE_AMBER = [214, 150, 34];

// ---- Geometry -----------------------------------------------------------------
// pixel units, 16px = 1 block. Small/low silhouette, pointed muzzle, big ears, long tail.
const elements = [
  { name: "body", from: [-2, 6, -5], to: [2, 11, 4], bone: "body" },
  { name: "head", from: [-2, 7, -9], to: [2, 11, -5], bone: "head" },
  { name: "snout", from: [-1, 7, -11], to: [1, 9, -9], bone: "head" },
  { name: "earL", from: [-2, 11, -8], to: [-1, 13, -6], bone: "head" },
  { name: "earR", from: [1, 11, -8], to: [2, 13, -6], bone: "head" },
  { name: "tailBase", from: [-1, 8, 4], to: [1, 10, 9], bone: "tail" },
  { name: "tailTip", from: [-1, 8, 9], to: [1, 10, 11], bone: "tail" },
  { name: "frontLegL", from: [-2, 0, -4], to: [0, 6, -2], bone: "frontLegL" },
  { name: "frontLegR", from: [0, 0, -4], to: [2, 6, -2], bone: "frontLegR" },
  { name: "backLegL", from: [-2, 0, 2], to: [0, 6, 4], bone: "backLegL" },
  { name: "backLegR", from: [0, 0, 2], to: [2, 6, 4], bone: "backLegR" },
];

const bones = {
  body: { origin: [0, 11, 0], rotation: [0, 0, 0] },
  head: { origin: [0, 11, -5], rotation: [0, 0, 0] },
  tail: { origin: [0, 10, 4], rotation: [-15, 0, 0] },
  frontLegL: { origin: [-1, 6, -3], rotation: [0, 0, 0] },
  frontLegR: { origin: [1, 6, -3], rotation: [0, 0, 0] },
  backLegL: { origin: [-1, 6, 3], rotation: [0, 0, 0] },
  backLegR: { origin: [1, 6, 3], rotation: [0, 0, 0] },
};

function uuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (rng() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function size(el) {
  return [Math.round(el.to[0] - el.from[0]), Math.round(el.to[1] - el.from[1]), Math.round(el.to[2] - el.from[2])];
}

// ---- Simple shelf UV packer: every face gets its own disjoint rect ------------
const PACK_WIDTH = 64;
let cursorX = 0;
let cursorY = 0;
let rowH = 0;
const PAD = 0;
function pack(w, h) {
  if (cursorX + w > PACK_WIDTH) {
    cursorX = 0;
    cursorY += rowH + PAD;
    rowH = 0;
  }
  const rect = [cursorX, cursorY, cursorX + w, cursorY + h];
  cursorX += w + PAD;
  rowH = Math.max(rowH, h);
  return rect;
}

const faceOrder = ["north", "south", "east", "west", "up", "down"];
const builtElements = [];
for (const el of elements) {
  const [w, h, d] = size(el);
  const faces = {};
  for (const face of faceOrder) {
    let fw, fh;
    if (face === "north" || face === "south") [fw, fh] = [w, h];
    else if (face === "east" || face === "west") [fw, fh] = [d, h];
    else [fw, fh] = [w, d];
    const rect = pack(fw, fh);
    faces[face] = { uv: rect, texture: 0 };
  }
  builtElements.push({
    name: el.name,
    box_uv: false,
    render_order: "default",
    locked: false,
    export: true,
    scope: 0,
    allow_mirror_modeling: false,
    from: el.from,
    to: el.to,
    autouv: 0,
    color: 0,
    origin: [0, 0, 0],
    faces,
    type: "cube",
    uuid: uuid(),
    _bone: el.bone,
  });
}
const RES_W = PACK_WIDTH;
const RES_H = cursorY + rowH;

// verify disjoint (sanity check as instructed)
function overlap(a, b) {
  return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
}
const allRects = builtElements.flatMap((el) => faceOrder.map((f) => el.faces[f].uv));
for (let i = 0; i < allRects.length; i++)
  for (let j = i + 1; j < allRects.length; j++)
    if (overlap(allRects[i], allRects[j])) throw new Error(`UV overlap ${i}/${j}`);

// ---- Bones / outliner ----------------------------------------------------------
const boneNames = Object.keys(bones);
const boneUuids = Object.fromEntries(boneNames.map((n) => [n, uuid()]));
const groups = boneNames.map((name) => ({
  name,
  uuid: boneUuids[name],
  export: true,
  locked: false,
  scope: 0,
  origin: bones[name].origin,
  rotation: bones[name].rotation,
  color: 0,
  children: [],
  reset: false,
  shade: true,
  mirror_uv: false,
  visibility: true,
  autouv: 0,
  isOpen: true,
  primary_selected: false,
}));
const outliner = boneNames.map((name) => ({
  uuid: boneUuids[name],
  isOpen: true,
  children: builtElements.filter((el) => el._bone === name).map((el) => el.uuid),
}));
for (const el of builtElements) delete el._bone;

// ---- Texture painting -----------------------------------------------------------
const canvas = new Uint8ClampedArray(RES_W * RES_H * 4); // starts fully transparent

function setPx(x, y, rgb, a = 255) {
  if (x < 0 || y < 0 || x >= RES_W || y >= RES_H) return;
  const i = (y * RES_W + x) * 4;
  canvas[i] = rgb[0];
  canvas[i + 1] = rgb[1];
  canvas[i + 2] = rgb[2];
  canvas[i + 3] = a;
}

function jitter(rgb, amount) {
  return rgb.map((c) => Math.max(0, Math.min(255, c + Math.round((rng() * 2 - 1) * amount))));
}

// coarse value-noise grid for mottling, per painted rect region reused globally
function valueNoise(x, y, cell) {
  const gx = x / cell, gy = y / cell;
  const x0 = Math.floor(gx), y0 = Math.floor(gy);
  const fx = gx - x0, fy = gy - y0;
  const h = (ix, iy) => {
    const s = Math.sin(ix * 127.1 + iy * 311.7 + SEED) * 43758.5453;
    return s - Math.floor(s);
  };
  const v00 = h(x0, y0), v10 = h(x0 + 1, y0), v01 = h(x0, y0 + 1), v11 = h(x0 + 1, y0 + 1);
  const a = v00 * (1 - fx) + v10 * fx;
  const b = v01 * (1 - fx) + v11 * fx;
  return a * (1 - fy) + b * fy;
}

function paintFurRect(rect, options = {}) {
  const [x1, y1, x2, y2] = rect;
  const belly = options.belly ?? false;
  const dark = options.dark ?? false;
  for (let y = y1; y < y2; y++) {
    for (let x = x1; x < x2; x++) {
      let base = dark ? FUR_ORANGE_DARK : FUR_ORANGE;
      if (belly) {
        // vertical gradient roux -> blanc toward the bottom of the rect (belly underside)
        const t = (y - y1) / Math.max(1, y2 - y1 - 1);
        base = base.map((c, i) => Math.round(c * (1 - t) + BELLY_WHITE[i] * t));
      }
      const mott = valueNoise(x, y, 3);
      if (mott > 0.62) base = base.map((c, i) => Math.round(c * 0.85 + FUR_ORANGE_DARK[i] * 0.15));
      const shadeY = y1 === rect[1] ? 1 : 1; // placeholder, AO applied below via face type
      setPx(x, y, jitter(base, 10));
    }
  }
}

function paintSock(rect) {
  const [x1, y1, x2, y2] = rect;
  for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) setPx(x, y, jitter(SOCK_BLACK, 8));
}

function paintTailTip(rect) {
  const [x1, y1, x2, y2] = rect;
  for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) setPx(x, y, jitter(BELLY_WHITE, 8));
}

for (const el of builtElements) {
  const isBelly = el.name === "body";
  const isLeg = el.name.includes("Leg");
  const isTailTip = el.name === "tailTip";
  for (const face of faceOrder) {
    const rect = el.faces[face].uv;
    if (isTailTip) paintTailTip(rect);
    else if (isLeg && (face === "down" || face === "north" || face === "south" || face === "east" || face === "west"))
      paintSock(rect);
    else paintFurRect(rect, { belly: isBelly && face === "down" });
  }
}

// AO: darken "down" faces slightly, lighten "up" faces slightly (cheap volume)
for (const el of builtElements) {
  for (const face of ["up", "down"]) {
    if (el.name === "tailTip") continue;
    const [x1, y1, x2, y2] = el.faces[face].uv;
    const delta = face === "up" ? 10 : -10;
    for (let y = y1; y < y2; y++)
      for (let x = x1; x < x2; x++) {
        const i = (y * RES_W + x) * 4;
        canvas[i] = Math.max(0, Math.min(255, canvas[i] + delta));
        canvas[i + 1] = Math.max(0, Math.min(255, canvas[i + 1] + delta));
        canvas[i + 2] = Math.max(0, Math.min(255, canvas[i + 2] + delta));
      }
  }
}

// Eyes: painted last on head's north face (front)
const headEl = builtElements.find((e) => e.name === "head");
{
  const [x1, y1, x2, y2] = headEl.faces.north.uv;
  const w = x2 - x1, h = y2 - y1;
  const eyeW = Math.max(1, Math.round(w * 0.18));
  const eyeH = Math.max(1, Math.round(h * 0.18));
  const ey = y1 + Math.round(h * 0.35);
  const exL = x1 + Math.round(w * 0.12);
  const exR = x2 - Math.round(w * 0.12) - eyeW;
  for (let y = ey; y < ey + eyeH; y++) {
    for (let x = exL; x < exL + eyeW; x++) setPx(x, y, EYE_AMBER);
    for (let x = exR; x < exR + eyeW; x++) setPx(x, y, EYE_AMBER);
  }
}

// Neighbor-bleed pass to guarantee full opacity (gutters / leftover packer space)
function bleedPass() {
  let changed = true;
  let iterations = 0;
  while (changed && iterations < 40) {
    changed = false;
    iterations++;
    for (let y = 0; y < RES_H; y++) {
      for (let x = 0; x < RES_W; x++) {
        const i = (y * RES_W + x) * 4;
        if (canvas[i + 3] !== 0) continue;
        const neighbors = [
          [x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1],
        ];
        for (const [nx, ny] of neighbors) {
          if (nx < 0 || ny < 0 || nx >= RES_W || ny >= RES_H) continue;
          const ni = (ny * RES_W + nx) * 4;
          if (canvas[ni + 3] === 255) {
            canvas[i] = canvas[ni];
            canvas[i + 1] = canvas[ni + 1];
            canvas[i + 2] = canvas[ni + 2];
            canvas[i + 3] = 255;
            changed = true;
            break;
          }
        }
      }
    }
  }
}
bleedPass();
// Fallback: any pixel still transparent (fully enclosed pocket) -> flat fill
for (let i = 0; i < canvas.length; i += 4) {
  if (canvas[i + 3] !== 255) {
    canvas[i] = FUR_ORANGE[0];
    canvas[i + 1] = FUR_ORANGE[1];
    canvas[i + 2] = FUR_ORANGE[2];
    canvas[i + 3] = 255;
  }
}

// ---- Minimal PNG encoder (RGBA8, zlib deflate) ---------------------------------
function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const typeBuf = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}
function encodePNG(width, height, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdrData = Buffer.alloc(13);
  ihdrData.writeUInt32BE(width, 0);
  ihdrData.writeUInt32BE(height, 4);
  ihdrData[8] = 8; // bit depth
  ihdrData[9] = 6; // color type RGBA
  ihdrData[10] = 0;
  ihdrData[11] = 0;
  ihdrData[12] = 0;
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter: None
    for (let x = 0; x < width * 4; x++) {
      raw[y * (width * 4 + 1) + 1 + x] = rgba[y * width * 4 + x];
    }
  }
  const idatData = deflateSync(raw);
  return Buffer.concat([sig, chunk("IHDR", ihdrData), chunk("IDAT", idatData), chunk("IEND", Buffer.alloc(0))]);
}

const png = encodePNG(RES_W, RES_H, canvas);
const base64 = png.toString("base64");

// ---- Assemble bbmodel -----------------------------------------------------------
const bbmodel = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "fox",
  resolution: { width: RES_W, height: RES_H },
  elements: builtElements,
  groups,
  outliner,
  textures: [
    {
      name: "fox",
      id: "0",
      width: RES_W,
      height: RES_H,
      internal: true,
      saved: false,
      uuid: uuid(),
      source: `data:image/png;base64,${base64}`,
    },
  ],
};

writeFileSync(OUT, JSON.stringify(bbmodel));
console.log(`Wrote ${OUT} (${RES_W}x${RES_H} texture, ${builtElements.length} elements)`);
