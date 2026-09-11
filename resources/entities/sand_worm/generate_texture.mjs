// Builds sand_worm.bbmodel geometry (deterministic) and paints its texture procedurally.
// Re-run (node generate_texture.mjs) to regenerate; bump SEED for a same-style variant.
import { writeFileSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, "sand_worm.bbmodel");

const SEED = 20260911 + 21;
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
const SAND = [196, 164, 110];
const SAND_DARK = [156, 128, 84];
const INTERIOR = [78, 36, 26];

// ---- Geometry: anneaux concentriques émergeant du sol, pas de membres, pas d'yeux -
const elements = [
  { name: "seg0", from: [-3, 0, -3], to: [3, 4, 3], bone: "body" },
  { name: "seg1", from: [-2, 4, -2], to: [2, 8, 2], bone: "body" },
  { name: "seg2", from: [-2, 8, -2], to: [2, 11, 2], bone: "body" },
  { name: "mouth", from: [-2, 11, -2], to: [2, 14, 2], bone: "body" },
];

const bones = { body: { origin: [0, 7, 0], rotation: [0, 0, 0] } };

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
function paintRing(rect, dark) {
  const [x1, y1, x2, y2] = rect;
  for (let y = y1; y < y2; y++)
    for (let x = x1; x < x2; x++) setPx(x, y, jitter(dark ? SAND_DARK : SAND, 9));
}

let ringIndex = 0;
for (const el of builtElements) {
  const dark = ringIndex % 2 === 1;
  for (const face of faceOrder) {
    const rect = el.faces[face].uv;
    if (el.name === "mouth" && face === "up") paintRing(rect, true), (() => {
      const [x1, y1, x2, y2] = rect;
      for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) setPx(x, y, jitter(INTERIOR, 8));
    })();
    else paintRing(rect, dark);
  }
  ringIndex++;
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
    canvas[i] = SAND[0];
    canvas[i + 1] = SAND[1];
    canvas[i + 2] = SAND[2];
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
  name: "sand_worm",
  resolution: { width: RES_W, height: RES_H },
  elements: builtElements,
  groups,
  outliner,
  textures: [
    {
      name: "sand_worm",
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
