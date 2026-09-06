// Gorilla NPC — geometry + procedural texture generator.
// Re-runnable: rebuilds resources/entities/gorilla/gorilla.bbmodel (elements, bones,
// outliner, per-face UV atlas) and paints the embedded PNG texture.
// Run: make dc CMD="node resources/entities/gorilla/generate_texture.mjs"

import zlib from "node:zlib";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const DIR = dirname(fileURLToPath(import.meta.url));
const BBMODEL = join(DIR, "gorilla.bbmodel");

// ---- tunables -------------------------------------------------------------
const SEED = 0x60471; // bump for a same-style variant
const ATLAS_W = 128;

const COLORS = {
  fur: [43, 43, 50], // anthracite body fur
  saddle: [138, 138, 148], // silverback saddle (lower back / hump)
  skin: [71, 71, 78], // face, muzzle, knuckles — bare skin
  eye: [216, 162, 74], // amber
};
const GRAIN = { fur: 13, saddle: 11, skin: 6 };

// ---- geometry ------------------------------------------------------------
// material: which palette + grain a face uses. saddleTop: faces that get the
// silverback overlay. 16px = 1 block, feet at y=0.
const BONES = [
  { name: "body", origin: [0, 13, 0] },
  { name: "head", origin: [0, 16, -8] },
  { name: "rightArm", origin: [-7, 16, -6] },
  { name: "leftArm", origin: [7, 16, -6] },
  { name: "rightLeg", origin: [-3, 10, 4] },
  { name: "leftLeg", origin: [3, 10, 4] },
];

const ELEMENTS = [
  { name: "torso", bone: "body", from: [-6, 9, -6], to: [6, 20, 7], mat: "fur", saddle: true },
  { name: "hump", bone: "body", from: [-5, 18, -8], to: [5, 26, 1], mat: "fur", saddle: true },
  { name: "head", bone: "head", from: [-4, 14, -15], to: [4, 22, -7], mat: "skin" },
  { name: "brow", bone: "head", from: [-4, 20, -16], to: [4, 22, -13], mat: "skin" },
  { name: "muzzle", bone: "head", from: [-3, 14, -17], to: [3, 18, -15], mat: "skin" },
  { name: "earR", bone: "head", from: [-6, 18, -11], to: [-4, 21, -9], mat: "fur" },
  { name: "earL", bone: "head", from: [4, 18, -11], to: [6, 21, -9], mat: "fur" },
  { name: "rightArm", bone: "rightArm", from: [-10, 0, -9], to: [-5, 16, -3], mat: "fur" },
  { name: "leftArm", bone: "leftArm", from: [5, 0, -9], to: [10, 16, -3], mat: "fur" },
  { name: "rightLeg", bone: "rightLeg", from: [-6, 0, 1], to: [-1, 10, 7], mat: "fur" },
  { name: "leftLeg", bone: "leftLeg", from: [1, 0, 1], to: [6, 10, 7], mat: "fur" },
];

const HEAD_FRONT_FACE = "north"; // head bone faces -Z

// face pixel dims from element size (Blockbench convention)
function faceDims(size, face) {
  const [w, h, d] = size;
  switch (face) {
    case "north":
    case "south":
      return [w, h];
    case "east":
    case "west":
      return [d, h];
    case "up":
    case "down":
      return [w, d];
  }
}

// ---- UV packer: disjoint rect per face by construction --------------------
const FACE_ORDER = ["north", "east", "south", "west", "up", "down"];
let cx = 0,
  cy = 0,
  rowH = 0;
const packed = []; // {el, face, uv:[x1,y1,x2,y2], mat, saddle}
for (const el of ELEMENTS) {
  const size = [el.to[0] - el.from[0], el.to[1] - el.from[1], el.to[2] - el.from[2]];
  el._size = size;
  el._faces = {};
  for (const face of FACE_ORDER) {
    const [w, h] = faceDims(size, face);
    if (cx + w > ATLAS_W) {
      cx = 0;
      cy += rowH;
      rowH = 0;
    }
    const uv = [cx, cy, cx + w, cy + h];
    el._faces[face] = uv;
    packed.push({ el, face, uv, mat: el.mat, saddle: !!el.saddle });
    cx += w;
    rowH = Math.max(rowH, h);
  }
}
const ATLAS_H = cy + rowH;

// no-overlap assertion
for (let i = 0; i < packed.length; i++) {
  for (let j = i + 1; j < packed.length; j++) {
    const [ax1, ay1, ax2, ay2] = packed[i].uv;
    const [bx1, by1, bx2, by2] = packed[j].uv;
    if (ax1 < bx2 && bx1 < ax2 && ay1 < by2 && by1 < ay2) {
      throw new Error(`UV overlap: ${packed[i].el.name}.${packed[i].face} vs ${packed[j].el.name}.${packed[j].face}`);
    }
  }
}
for (const p of packed) {
  if (p.uv.some((v) => !Number.isInteger(v))) throw new Error(`non-integer UV on ${p.el.name}.${p.face}`);
}

// ---- PRNG + noise -------------------------------------------------------
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rnd = mulberry32(SEED);

function valueNoiseField(w, h) {
  const g = 6;
  const grid = Array.from({ length: g + 1 }, () => Array.from({ length: g + 1 }, () => rnd()));
  return (x, y) => {
    const fx = (x / Math.max(1, w)) * g,
      fy = (y / Math.max(1, h)) * g;
    const x0 = Math.min(g - 1, Math.floor(fx)),
      y0 = Math.min(g - 1, Math.floor(fy));
    const tx = fx - x0,
      ty = fy - y0;
    const a = grid[y0][x0],
      b = grid[y0][x0 + 1],
      c = grid[y0 + 1][x0],
      d = grid[y0 + 1][x0 + 1];
    return a * (1 - tx) * (1 - ty) + b * tx * (1 - ty) + c * (1 - tx) * ty + d * tx * ty;
  };
}

const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);
const shade = { north: 0, south: -6, east: -10, west: -10, up: 12, down: -16 };

// ---- canvas ------------------------------------------------------------
const canvas = new Uint8Array(ATLAS_W * ATLAS_H * 4); // RGBA, starts transparent
function setPx(x, y, r, g, b) {
  const i = (y * ATLAS_W + x) * 4;
  canvas[i] = r;
  canvas[i + 1] = g;
  canvas[i + 2] = b;
  canvas[i + 3] = 255;
}

for (const p of packed) {
  const [x1, y1, x2, y2] = p.uv;
  const fw = x2 - x1,
    fh = y2 - y1;
  const base = COLORS[p.mat];
  const grain = GRAIN[p.mat];
  const noise = valueNoiseField(fw, fh);
  const sh = shade[p.face] ?? 0;
  // saddle only on upper faces of body elements (up + rear-facing south)
  const saddleFace = p.saddle && (p.face === "up" || p.face === "south");
  for (let fy = 0; fy < fh; fy++) {
    for (let fx = 0; fx < fw; fx++) {
      let col = base.slice();
      // macro mottling toward a darker fur tone
      const n = noise(fx, fy);
      if (p.mat !== "skin") {
        const dark = [Math.max(0, base[0] - 14), Math.max(0, base[1] - 14), Math.max(0, base[2] - 12)];
        const t = Math.max(0, (n - 0.45) * 1.3);
        col = col.map((c, k) => c * (1 - t) + dark[k] * t);
      }
      if (saddleFace) {
        const t = Math.min(1, Math.max(0, (n - 0.3) * 1.4)) * 0.85;
        col = col.map((c, k) => c * (1 - t) + COLORS.saddle[k] * t);
      }
      const j = (rnd() - 0.5) * 2 * grain;
      setPx(x1 + fx, y1 + fy, clamp(col[0] + j + sh), clamp(col[1] + j + sh), clamp(col[2] + j + sh));
    }
  }
}

// ---- eyes: painted last on head front face ----------------------------
{
  const head = ELEMENTS.find((e) => e.name === "head");
  const [x1, y1, x2, y2] = head._faces[HEAD_FRONT_FACE];
  const fw = x2 - x1,
    fh = y2 - y1;
  const eyeW = Math.max(1, Math.round(fw * 0.16));
  const eyeH = Math.max(1, Math.round(fh * 0.16));
  const ey = y1 + Math.round(fh * 0.42);
  const insets = [Math.round(fw * 0.16), fw - Math.round(fw * 0.16) - eyeW];
  for (const ex0 of insets) {
    for (let dy = 0; dy < eyeH; dy++) {
      for (let dx = 0; dx < eyeW; dx++) {
        setPx(x1 + ex0 + dx, ey + dy, COLORS.eye[0], COLORS.eye[1], COLORS.eye[2]);
      }
    }
  }
}

// ---- opacity: bleed then force-fill ----------------------------------
function bleed(iterations) {
  for (let it = 0; it < iterations; it++) {
    let changed = 0;
    const snap = canvas.slice();
    for (let y = 0; y < ATLAS_H; y++) {
      for (let x = 0; x < ATLAS_W; x++) {
        const i = (y * ATLAS_W + x) * 4;
        if (snap[i + 3] === 255) continue;
        const nb = [
          [x - 1, y],
          [x + 1, y],
          [x, y - 1],
          [x, y + 1],
        ];
        for (const [nx, ny] of nb) {
          if (nx < 0 || ny < 0 || nx >= ATLAS_W || ny >= ATLAS_H) continue;
          const ni = (ny * ATLAS_W + nx) * 4;
          if (snap[ni + 3] === 255) {
            canvas[i] = snap[ni];
            canvas[i + 1] = snap[ni + 1];
            canvas[i + 2] = snap[ni + 2];
            canvas[i + 3] = 255;
            changed++;
            break;
          }
        }
      }
    }
    if (!changed) break;
  }
}
bleed(Math.max(ATLAS_W, ATLAS_H));
for (let p = 0; p < ATLAS_W * ATLAS_H; p++) {
  if (canvas[p * 4 + 3] !== 255) {
    canvas[p * 4] = COLORS.fur[0];
    canvas[p * 4 + 1] = COLORS.fur[1];
    canvas[p * 4 + 2] = COLORS.fur[2];
    canvas[p * 4 + 3] = 255;
  }
}
for (let p = 0; p < ATLAS_W * ATLAS_H; p++) {
  if (canvas[p * 4 + 3] !== 255) throw new Error("transparent pixel survived");
}

// ---- minimal PNG encoder --------------------------------------------
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
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function encodePng(w, h, rgba) {
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) {
    raw[y * (1 + w * 4)] = 0;
    rgba.subarray(y * w * 4, (y + 1) * w * 4).forEach((v, i) => {
      raw[y * (1 + w * 4) + 1 + i] = v;
    });
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}

const png = encodePng(ATLAS_W, ATLAS_H, canvas);
const dataUrl = "data:image/png;base64," + png.toString("base64");

// ---- assemble bbmodel ----------------------------------------------
function uuid() {
  const h = "0123456789abcdef";
  let s = "";
  for (let i = 0; i < 36; i++) {
    if (i === 8 || i === 13 || i === 18 || i === 23) s += "-";
    else if (i === 14) s += "4";
    else if (i === 19) s += h[(Math.floor(rnd() * 16) & 0x3) | 0x8];
    else s += h[Math.floor(rnd() * 16)];
  }
  return s;
}

const boneUuid = Object.fromEntries(BONES.map((b) => [b.name, uuid()]));
const elemNodes = [];
const outElems = {};
const elements = ELEMENTS.map((el) => {
  const id = uuid();
  outElems[el.bone] = outElems[el.bone] || [];
  outElems[el.bone].push(id);
  const faces = {};
  for (const f of FACE_ORDER) faces[f] = { uv: el._faces[f], texture: 0 };
  return {
    name: el.name,
    box_uv: false,
    render_order: "default",
    locked: false,
    export: true,
    allow_mirror_modeling: false,
    from: el.from,
    to: el.to,
    autouv: 0,
    color: 0,
    origin: [0, 0, 0],
    faces,
    type: "cube",
    uuid: id,
  };
});

const groups = BONES.map((b) => ({
  name: b.name,
  uuid: boneUuid[b.name],
  export: true,
  locked: false,
  origin: b.origin,
  rotation: [0, 0, 0],
  color: 0,
  children: [],
  isOpen: true,
}));

const outliner = BONES.map((b) => ({
  uuid: boneUuid[b.name],
  isOpen: true,
  children: outElems[b.name] || [],
}));

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "gorilla",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements,
  groups,
  outliner,
  textures: [
    {
      name: "gorilla",
      id: "0",
      width: ATLAS_W,
      height: ATLAS_H,
      uv_width: ATLAS_W,
      uv_height: ATLAS_H,
      internal: true,
      saved: false,
      source: dataUrl,
    },
  ],
};

writeFileSync(BBMODEL, JSON.stringify(model, null, 2));
console.log(`wrote ${BBMODEL}  atlas ${ATLAS_W}x${ATLAS_H}  ${elements.length} elements  ${groups.length} bones`);
