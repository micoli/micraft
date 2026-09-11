// Builds harpy.bbmodel geometry (deterministic) and paints its texture procedurally.
// Re-run (node generate_texture.mjs) to regenerate; bump SEED for a same-style variant.
import { writeFileSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, "harpy.bbmodel");

const SEED = 20260911 + 33;
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
const PLUME = [92, 80, 68];
const PLUME_DARK = [64, 54, 44];
const SKIN = [196, 158, 124];
const EYE_FIERCE = [160, 40, 30];

// ---- Geometry: torse/tête humanoïdes, ailes en guise de bras, pattes griffues ---
const elements = [
  { name: "body", from: [-3, 12, -2], to: [3, 20, 2], bone: "body" },
  { name: "head", from: [-3, 20, -3], to: [3, 25, 3], bone: "head" },
  { name: "wingL", from: [-8, 15, -2], to: [-3, 20, 3], bone: "wingL" },
  { name: "wingR", from: [3, 15, -2], to: [8, 20, 3], bone: "wingR" },
  { name: "legL", from: [-2, 0, -1], to: [-1, 12, 1], bone: "legL" },
  { name: "legR", from: [1, 0, -1], to: [2, 12, 1], bone: "legR" },
];

const bones = {
  body: { origin: [0, 12, 0], rotation: [0, 0, 0] },
  head: { origin: [0, 20, 0], rotation: [0, 0, 0] },
  wingL: { origin: [-3, 20, 0], rotation: [0, 0, 0] },
  wingR: { origin: [3, 20, 0], rotation: [0, 0, 0] },
  legL: { origin: [-1.5, 12, 0], rotation: [0, 0, 0] },
  legR: { origin: [1.5, 12, 0], rotation: [0, 0, 0] },
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

const PACK_WIDTH = 64;
let cursorX = 0, cursorY = 0, rowH = 0;
function pack(w, h) {
  if (cursorX + w > PACK_WIDTH) {
    cursorX = 0;
    cursorY += rowH;
    rowH = 0;
  }
  const rect = [cursorX, cursorY, cursorX + w, cursorY + h];
  cursorX += w;
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
    faces[face] = { uv: pack(fw, fh), texture: 0 };
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

function overlap(a, b) {
  return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
}
const allRects = builtElements.flatMap((el) => faceOrder.map((f) => el.faces[f].uv));
for (let i = 0; i < allRects.length; i++)
  for (let j = i + 1; j < allRects.length; j++)
    if (overlap(allRects[i], allRects[j])) throw new Error(`UV overlap ${i}/${j}`);

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

const canvas = new Uint8ClampedArray(RES_W * RES_H * 4);
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
function paintPlumage(rect) {
  const [x1, y1, x2, y2] = rect;
  for (let y = y1; y < y2; y++) {
    for (let x = x1; x < x2; x++) {
      let base = PLUME;
      const mott = valueNoise(x, y, 2.5);
      if (mott > 0.6) base = PLUME_DARK;
      setPx(x, y, jitter(base, 9));
    }
  }
}
function paintFlat(rect, rgb, amount = 6) {
  const [x1, y1, x2, y2] = rect;
  for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) setPx(x, y, jitter(rgb, amount));
}

for (const el of builtElements) {
  for (const face of faceOrder) {
    const rect = el.faces[face].uv;
    if (el.name === "head") paintFlat(rect, SKIN, 6);
    else if (el.name === "legL" || el.name === "legR") paintFlat(rect, PLUME_DARK, 4);
    else paintPlumage(rect);
  }
}

for (const el of builtElements) {
  for (const face of ["up", "down"]) {
    const [x1, y1, x2, y2] = el.faces[face].uv;
    const delta = face === "up" ? 8 : -8;
    for (let y = y1; y < y2; y++)
      for (let x = x1; x < x2; x++) {
        const i = (y * RES_W + x) * 4;
        canvas[i] = Math.max(0, Math.min(255, canvas[i] + delta));
        canvas[i + 1] = Math.max(0, Math.min(255, canvas[i + 1] + delta));
        canvas[i + 2] = Math.max(0, Math.min(255, canvas[i + 2] + delta));
      }
  }
}

const headEl = builtElements.find((e) => e.name === "head");
{
  const [x1, y1, x2, y2] = headEl.faces.north.uv;
  const w = x2 - x1, h = y2 - y1;
  const eyeW = Math.max(1, Math.round(w * 0.16));
  const eyeH = Math.max(1, Math.round(h * 0.14));
  const ey = y1 + Math.round(h * 0.35);
  const exL = x1 + Math.round(w * 0.16);
  const exR = x2 - Math.round(w * 0.16) - eyeW;
  for (let y = ey; y < ey + eyeH; y++) {
    for (let x = exL; x < exL + eyeW; x++) setPx(x, y, EYE_FIERCE);
    for (let x = exR; x < exR + eyeW; x++) setPx(x, y, EYE_FIERCE);
  }
}

function bleedPass() {
  let changed = true, iterations = 0;
  while (changed && iterations < 40) {
    changed = false;
    iterations++;
    for (let y = 0; y < RES_H; y++) {
      for (let x = 0; x < RES_W; x++) {
        const i = (y * RES_W + x) * 4;
        if (canvas[i + 3] !== 0) continue;
        const neighbors = [[x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1]];
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
for (let i = 0; i < canvas.length; i += 4) {
  if (canvas[i + 3] !== 255) {
    canvas[i] = PLUME[0];
    canvas[i + 1] = PLUME[1];
    canvas[i + 2] = PLUME[2];
    canvas[i + 3] = 255;
  }
}

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
  ihdrData[8] = 8;
  ihdrData[9] = 6;
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    for (let x = 0; x < width * 4; x++) raw[y * (width * 4 + 1) + 1 + x] = rgba[y * width * 4 + x];
  }
  const idatData = deflateSync(raw);
  return Buffer.concat([sig, chunk("IHDR", ihdrData), chunk("IDAT", idatData), chunk("IEND", Buffer.alloc(0))]);
}

const png = encodePNG(RES_W, RES_H, canvas);
const base64 = png.toString("base64");

const bbmodel = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "harpy",
  resolution: { width: RES_W, height: RES_H },
  elements: builtElements,
  groups,
  outliner,
  textures: [
    {
      name: "harpy",
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
