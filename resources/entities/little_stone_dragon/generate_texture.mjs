// Regenerates resources/entities/little_stone_dragon/little_stone_dragon.bbmodel — geometry
// (bones + cuboids with explicit non-overlapping per-face UV rects) and the procedurally painted
// texture embedded in textures[0].source. Re-runnable: tweak the CONSTANTS block or bump SEED for
// a same-style variant, then
//   make dc CMD="node resources/entities/little_stone_dragon/generate_texture.mjs"
//
// little_stone_dragon: a small faceted-stone dragon — raised horned head, dorsal spikes, bat
// wings, arrow-tipped tail, four stout legs. Slate grey-green matte rock, glowing amber eyes.
// Hostile, breathes fire.

import zlib from "node:zlib";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const BBMODEL = join(HERE, "little_stone_dragon.bbmodel");

// ---------------------------------------------------------------------------- constants
const SEED = 0x5354304e; // "ST0N"
const ATLAS_W = 64;

const COLORS = {
  stone: { base: [92, 102, 92], accent: [62, 70, 63], grain: 11, mix: 0.4 },
  wing: { base: [104, 112, 100], accent: [76, 83, 74], grain: 8, mix: 0.4 },
  horn: { base: [56, 60, 57], accent: [39, 42, 40], grain: 8, mix: 0.45 },
};
const NOSTRIL = [30, 33, 31];
const EYE_IRIS = [255, 176, 60]; // incandescent amber
const EYE_CORE = [255, 220, 140];

// low-poly stone reads best with strong per-face facet shading
const FACE_SHADE = { north: -4, south: -8, east: -12, west: 8, up: 20, down: -22 };
const FACE_ORDER = ["north", "east", "south", "west", "up", "down"];

// ---------------------------------------------------------------------------- geometry
// pixels, 16 px = 1 block, feet at y=0, front = -Z
const ELEMENTS = [
  { name: "body", from: [-4, 9, -7], to: [4, 17, 7], skin: "stone" },
  { name: "neck", from: [-2, 15, -11], to: [2, 21, -6], skin: "stone" },
  { name: "head", from: [-3, 19, -16], to: [3, 24, -10], skin: "stone" },
  { name: "snout", from: [-2, 18, -19], to: [2, 21, -16], skin: "stone" },
  { name: "hornL", from: [1, 23, -12], to: [3, 29, -10], skin: "horn" },
  { name: "hornR", from: [-3, 23, -12], to: [-1, 29, -10], skin: "horn" },
  { name: "earL", from: [2, 21, -11], to: [3, 24, -9], skin: "horn" },
  { name: "earR", from: [-3, 21, -11], to: [-2, 24, -9], skin: "horn" },
  { name: "spike1", from: [-1, 21, -8], to: [1, 24, -6], skin: "horn" },
  { name: "spike2", from: [-1, 17, -3], to: [1, 20, -1], skin: "horn" },
  { name: "spike3", from: [-1, 16, 2], to: [1, 19, 4], skin: "horn" },
  { name: "tail", from: [-2, 10, 7], to: [2, 15, 12], skin: "stone" },
  { name: "tailTip", from: [-2, 9, 12], to: [2, 14, 16], skin: "horn" },
  { name: "frontLegL", from: [-4, 0, -6], to: [-1, 9, -3], skin: "stone" },
  { name: "frontLegR", from: [1, 0, -6], to: [4, 9, -3], skin: "stone" },
  { name: "backLegL", from: [-4, 0, 3], to: [-1, 9, 6], skin: "stone" },
  { name: "backLegR", from: [1, 0, 3], to: [4, 9, 6], skin: "stone" },
  { name: "frontFootL", from: [-4, 0, -8], to: [-1, 2, -3], skin: "stone" },
  { name: "frontFootR", from: [1, 0, -8], to: [4, 2, -3], skin: "stone" },
  { name: "backFootL", from: [-4, 0, 3], to: [-1, 2, 8], skin: "stone" },
  { name: "backFootR", from: [1, 0, 3], to: [4, 2, 8], skin: "stone" },
  { name: "wingL", from: [4, 15, -2], to: [5, 26, 8], skin: "wing" },
  { name: "wingR", from: [-5, 15, -2], to: [-4, 26, 8], skin: "wing" },
];

// bone tree — nested; walk anim keys the alias bones set in the yaml
const TREE = {
  name: "body",
  origin: [0, 12, 0],
  rotation: [0, 0, 0],
  elems: ["body", "spike2", "spike3"],
  children: [
    {
      name: "neck",
      origin: [0, 16, -6],
      rotation: [-45, 0, 0],
      elems: ["neck", "spike1"],
      children: [
        {
          name: "head",
          origin: [0, 20, -11],
          rotation: [12, 0, 0],
          elems: ["head", "snout", "hornL", "hornR", "earL", "earR"],
          children: [],
        },
      ],
    },
    { name: "tail", origin: [0, 12, 7], rotation: [-18, 0, 0], elems: ["tail", "tailTip"], children: [] },
    { name: "frontLegL", origin: [-2, 9, -4], rotation: [0, 0, 0], elems: ["frontLegL", "frontFootL"], children: [] },
    { name: "frontLegR", origin: [2, 9, -4], rotation: [0, 0, 0], elems: ["frontLegR", "frontFootR"], children: [] },
    { name: "backLegL", origin: [-2, 9, 4], rotation: [0, 0, 0], elems: ["backLegL", "backFootL"], children: [] },
    { name: "backLegR", origin: [2, 9, 4], rotation: [0, 0, 0], elems: ["backLegR", "backFootR"], children: [] },
    { name: "wingL", origin: [4, 16, -1], rotation: [0, 0, 18], elems: ["wingL"], children: [] },
    { name: "wingR", origin: [-4, 16, -1], rotation: [0, 0, -18], elems: ["wingR"], children: [] },
  ],
};

// ---------------------------------------------------------------------------- prng / helpers
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
const clamp8 = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);
const lerp = (a, b, t) => a + (b - a) * t;

let uuidState = SEED >>> 0;
function uuid() {
  const h = [];
  for (let i = 0; i < 32; i++) {
    uuidState = (uuidState * 1664525 + 1013904223) >>> 0;
    h.push(((uuidState >>> 24) & 0xf).toString(16));
  }
  h[12] = "4";
  h[16] = ((parseInt(h[16], 16) & 0x3) | 0x8).toString(16);
  return (
    h.slice(0, 8).join("") + "-" + h.slice(8, 12).join("") + "-" + h.slice(12, 16).join("") + "-" +
    h.slice(16, 20).join("") + "-" + h.slice(20, 32).join("")
  );
}

function faceSize(el, face) {
  const dx = el.to[0] - el.from[0];
  const dy = el.to[1] - el.from[1];
  const dz = el.to[2] - el.from[2];
  if (face === "north" || face === "south") return [dx, dy];
  if (face === "east" || face === "west") return [dz, dy];
  return [dx, dz];
}

// ---------------------------------------------------------------------------- UV packing
let cursorX = 0;
let cursorY = 0;
let rowH = 0;
const rects = {};
for (const el of ELEMENTS) {
  for (const face of FACE_ORDER) {
    const [w, h] = faceSize(el, face);
    if (cursorX + w > ATLAS_W) {
      cursorX = 0;
      cursorY += rowH;
      rowH = 0;
    }
    rects[`${el.name}/${face}`] = [cursorX, cursorY, cursorX + w, cursorY + h];
    cursorX += w;
    if (h > rowH) rowH = h;
  }
}
const ATLAS_H = cursorY + rowH;

{
  const list = Object.entries(rects);
  for (let i = 0; i < list.length; i++) {
    for (let j = i + 1; j < list.length; j++) {
      const A = list[i][1];
      const B = list[j][1];
      if (A[0] < B[2] && B[0] < A[2] && A[1] < B[3] && B[1] < A[3]) {
        throw new Error(`UV overlap: ${list[i][0]} vs ${list[j][0]}`);
      }
    }
  }
  for (const [k, r] of list) {
    if (r.some((v) => !Number.isInteger(v))) throw new Error(`non-integer UV: ${k}`);
  }
}

// ---------------------------------------------------------------------------- texture paint
const px = new Uint8Array(ATLAS_W * ATLAS_H * 4);
function setPx(x, y, [r, g, b]) {
  const i = (y * ATLAS_W + x) * 4;
  px[i] = r;
  px[i + 1] = g;
  px[i + 2] = b;
  px[i + 3] = 255;
}
function noiseGrid(n) {
  const g = new Float64Array(n * n);
  for (let i = 0; i < g.length; i++) g[i] = rng();
  return g;
}
function sampleNoise(grid, n, u, v) {
  const gx = u * (n - 1);
  const gy = v * (n - 1);
  const x0 = Math.floor(gx);
  const y0 = Math.floor(gy);
  const x1 = Math.min(x0 + 1, n - 1);
  const y1 = Math.min(y0 + 1, n - 1);
  const tx = gx - x0;
  const ty = gy - y0;
  const a = lerp(grid[y0 * n + x0], grid[y0 * n + x1], tx);
  const b = lerp(grid[y1 * n + x0], grid[y1 * n + x1], tx);
  return lerp(a, b, ty);
}

for (const el of ELEMENTS) {
  const spec = COLORS[el.skin];
  const grid = noiseGrid(8);
  for (const face of FACE_ORDER) {
    const [x1, y1, x2, y2] = rects[`${el.name}/${face}`];
    const w = x2 - x1;
    const h = y2 - y1;
    const shade = FACE_SHADE[face];
    for (let ly = 0; ly < h; ly++) {
      for (let lx = 0; lx < w; lx++) {
        const u = w > 1 ? lx / (w - 1) : 0;
        const v = h > 1 ? ly / (h - 1) : 0;
        const mix = spec.mix * sampleNoise(grid, 8, u, v);
        const out = [0, 0, 0];
        for (let c = 0; c < 3; c++) {
          let val = lerp(spec.base[c], spec.accent[c], mix);
          val += (rng() * 2 - 1) * spec.grain;
          val += shade;
          out[c] = clamp8(val);
        }
        setPx(x1 + lx, y1 + ly, out);
      }
    }
  }
}

// nostrils on the snout forward (north) face
{
  const [x1, y1, x2] = rects["snout/north"];
  const w = x2 - x1;
  setPx(x1 + Math.floor(w * 0.25), y1 + 1, NOSTRIL);
  setPx(x1 + Math.floor(w * 0.7), y1 + 1, NOSTRIL);
}

// glowing amber eyes on the head forward (north) face — painted last, no grain
{
  const [hx1, hy1, hx2, hy2] = rects["head/north"];
  const w = hx2 - hx1;
  const h = hy2 - hy1;
  const eyeY = hy1 + Math.round(h * 0.28);
  const lxs = [hx1 + Math.round(w * 0.14), hx1 + Math.round(w * 0.62)];
  for (const ex of lxs) {
    for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) setPx(ex + dx, eyeY + dy, EYE_IRIS);
    setPx(ex + 1, eyeY, EYE_CORE);
  }
}

// ---------------------------------------------------------------------------- opacity fill
function bleed(iterations) {
  for (let it = 0; it < iterations; it++) {
    let changed = false;
    const snap = px.slice();
    for (let y = 0; y < ATLAS_H; y++) {
      for (let x = 0; x < ATLAS_W; x++) {
        const i = (y * ATLAS_W + x) * 4;
        if (snap[i + 3] === 255) continue;
        for (const [nx, ny] of [
          [x - 1, y],
          [x + 1, y],
          [x, y - 1],
          [x, y + 1],
        ]) {
          if (nx < 0 || ny < 0 || nx >= ATLAS_W || ny >= ATLAS_H) continue;
          const j = (ny * ATLAS_W + nx) * 4;
          if (snap[j + 3] !== 255) continue;
          px[i] = snap[j];
          px[i + 1] = snap[j + 1];
          px[i + 2] = snap[j + 2];
          px[i + 3] = 255;
          changed = true;
          break;
        }
      }
    }
    if (!changed) break;
  }
}
bleed(ATLAS_W + ATLAS_H);
for (let i = 0; i < px.length; i += 4) {
  if (px[i + 3] !== 255) {
    px[i] = COLORS.stone.base[0];
    px[i + 1] = COLORS.stone.base[1];
    px[i + 2] = COLORS.stone.base[2];
    px[i + 3] = 255;
  }
}
for (let i = 3; i < px.length; i += 4) {
  if (px[i] !== 255) throw new Error("transparent pixel survived opacity fill");
}

// ---------------------------------------------------------------------------- PNG encode
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const t = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([t, data])), 0);
  return Buffer.concat([len, t, data, crc]);
}
function encodePng(w, h, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) {
    raw[y * (1 + w * 4)] = 0;
    rgba.copy(raw, y * (1 + w * 4) + 1, y * w * 4, (y + 1) * w * 4);
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}
const pngBuf = encodePng(ATLAS_W, ATLAS_H, Buffer.from(px.buffer, px.byteOffset, px.byteLength));
const dataUrl = "data:image/png;base64," + pngBuf.toString("base64");

// ---------------------------------------------------------------------------- bbmodel assembly
const elemUuid = {};
for (const el of ELEMENTS) elemUuid[el.name] = uuid();

function facesFor(el) {
  const f = {};
  for (const face of FACE_ORDER) f[face] = { uv: rects[`${el.name}/${face}`], texture: 0 };
  return f;
}

const elements = ELEMENTS.map((el) => ({
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
  faces: facesFor(el),
  type: "cube",
  uuid: elemUuid[el.name],
}));

const groups = [];
function walk(node) {
  const gid = uuid();
  groups.push({
    name: node.name,
    uuid: gid,
    export: true,
    locked: false,
    scope: 0,
    selected: false,
    origin: node.origin,
    rotation: node.rotation,
    color: 0,
    children: [],
    reset: false,
    shade: true,
    mirror_uv: false,
    visibility: true,
    autouv: 0,
    isOpen: true,
    primary_selected: false,
  });
  return {
    uuid: gid,
    isOpen: true,
    children: [...node.elems.map((n) => elemUuid[n]), ...node.children.map(walk)],
  };
}
const outliner = [walk(TREE)];

const bbmodel = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "little_stone_dragon",
  model_identifier: "",
  visible_box: [1, 1, 0],
  variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements,
  groups,
  outliner,
  textures: [
    {
      name: "little_stone_dragon",
      path: "",
      folder: "",
      namespace: "",
      id: "0",
      group: "",
      scope: 0,
      width: ATLAS_W,
      height: ATLAS_H,
      uv_width: ATLAS_W,
      uv_height: ATLAS_H,
      particle: false,
      use_as_default: false,
      layers_enabled: false,
      sync_to_project: "",
      file_format: "png",
      render_mode: "default",
      render_sides: "auto",
      wrap_mode: "limited",
      pbr_channel: "color",
      fps: 7,
      frame_time: 1,
      frame_order_type: "loop",
      frame_order: "",
      frame_interpolate: false,
      visible: true,
      internal: true,
      saved: false,
      uuid: uuid(),
      source: dataUrl,
    },
  ],
};

writeFileSync(BBMODEL, JSON.stringify(bbmodel));
console.log(`wrote ${BBMODEL}`);
console.log(`atlas ${ATLAS_W}x${ATLAS_H}, ${ELEMENTS.length} elements, ${groups.length} bones, ${pngBuf.length} B png`);
