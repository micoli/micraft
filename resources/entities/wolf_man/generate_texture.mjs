// Regenerates wolf_man.bbmodel (geometry UV packing + embedded texture) from the
// element table below. Re-runnable: tweak the constants and re-run
//   make dc CMD="node resources/entities/wolf_man/generate_texture.mjs"
// A werewolf: humanoid rig, light-grey fur head/hands/paws/tail, brown leather
// torso and limbs, wolf ears + muzzle, amber eyes.
import zlib from "node:zlib";
import { writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";

const OUT = new URL("./wolf_man.bbmodel", import.meta.url);
const RNG_SEED = 20260906;
const ATLAS_W = 64;

// ── palette ──
const FUR = [176, 179, 183]; // light grey fur
const FUR_DARK = [126, 128, 133]; // mottling / paw / ear inner
const LEATHER = [92, 63, 41]; // brown leather garment
const LEATHER_DARK = [58, 39, 24]; // seams / shading
const EYE = [224, 172, 44]; // amber

const GRAIN_FUR = 13;
const GRAIN_LEATHER = 8;

// ── mulberry32 ──
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = mulberry32(RNG_SEED);
const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);

// value-noise field per element for macro mottling
function noiseField(gw, gh) {
  const g = new Float64Array(gw * gh);
  for (let i = 0; i < g.length; i++) g[i] = rng();
  return (u, v) => {
    const x = u * (gw - 1);
    const y = v * (gh - 1);
    const x0 = Math.floor(x);
    const y0 = Math.floor(y);
    const x1 = Math.min(x0 + 1, gw - 1);
    const y1 = Math.min(y0 + 1, gh - 1);
    const fx = x - x0;
    const fy = y - y0;
    const a = g[y0 * gw + x0];
    const b = g[y0 * gw + x1];
    const c = g[y1 * gw + x0];
    const d = g[y1 * gw + x1];
    return (
      a * (1 - fx) * (1 - fy) +
      b * fx * (1 - fy) +
      c * (1 - fx) * fy +
      d * fx * fy
    );
  };
}

// ── element table (pixels, 16px = 1 block, feet at y=0) ──
// mat: how each face is painted. "furLower" = leather with the bottom `lower`
// fraction painted as fur (hand / paw); "down"/"up" overrides for cap faces.
const S = 1;
const elements = [
  { name: "body", from: [-5, 15, -3], to: [5, 29, 3], bone: "body", mat: "leather" },
  { name: "head", from: [-4, 29, -4], to: [4, 37, 4], bone: "head", mat: "fur" },
  { name: "earR", from: [-7, 36, -2], to: [-4, 40, 1], bone: "head", mat: "fur" },
  { name: "earL", from: [4, 36, -2], to: [7, 40, 1], bone: "head", mat: "fur" },
  { name: "muzzle", from: [-2, 29, -7], to: [2, 33, -4], bone: "head", mat: "fur" },
  { name: "rightArm", from: [-9, 15, -2], to: [-5, 29, 2], bone: "rightArm", mat: "armLeather" },
  { name: "leftArm", from: [5, 15, -2], to: [9, 29, 2], bone: "leftArm", mat: "armLeather" },
  { name: "rightLeg", from: [-5, 0, -2], to: [-1, 15, 2], bone: "rightLeg", mat: "legLeather" },
  { name: "leftLeg", from: [1, 0, -2], to: [5, 15, 2], bone: "leftLeg", mat: "legLeather" },
  { name: "tail", from: [-1, 9, 3], to: [2, 17, 7], bone: "tail", mat: "furTail" },
];

const bones = {
  body: { origin: [0, 15, 0], parent: null },
  head: { origin: [0, 29, 0], parent: null },
  rightArm: { origin: [-5, 28, 0], parent: null },
  leftArm: { origin: [5, 28, 0], parent: null },
  rightLeg: { origin: [-2, 15, 0], parent: null },
  leftLeg: { origin: [2, 15, 0], parent: null },
  tail: { origin: [0, 17, 3], parent: null },
};

const FACES = ["north", "east", "south", "west", "up", "down"];
function faceSize(el, f) {
  const dx = el.to[0] - el.from[0];
  const dy = el.to[1] - el.from[1];
  const dz = el.to[2] - el.from[2];
  if (f === "north" || f === "south") return [dx, dy];
  if (f === "east" || f === "west") return [dz, dy];
  return [dx, dz]; // up / down
}

// ── pack every face into a disjoint rect (1px gutter) ──
let cx = 1;
let cy = 1;
let rowH = 0;
let atlasH = 1;
for (const el of elements) {
  el.uv = {};
  for (const f of FACES) {
    let [w, h] = faceSize(el, f);
    if (!Number.isInteger(w) || !Number.isInteger(h) || w <= 0 || h <= 0) {
      throw new Error(`non-integer face size ${el.name}.${f}: ${w}x${h}`);
    }
    if (cx + w + 1 > ATLAS_W) {
      cx = 1;
      cy += rowH + 1;
      rowH = 0;
    }
    el.uv[f] = [cx, cy, cx + w, cy + h];
    cx += w + 1;
    rowH = Math.max(rowH, h);
    atlasH = Math.max(atlasH, cy + h + 1);
  }
}
const ATLAS_H = atlasH;

// ── overlap check ──
const rects = [];
for (const el of elements) for (const f of FACES) rects.push([el.name, f, el.uv[f]]);
for (let i = 0; i < rects.length; i++) {
  for (let j = i + 1; j < rects.length; j++) {
    const [an, af, a] = rects[i];
    const [bn, bf, b] = rects[j];
    if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]) {
      throw new Error(`UV overlap ${an}.${af} vs ${bn}.${bf}`);
    }
  }
}

// ── canvas ──
const px = new Uint8Array(ATLAS_W * ATLAS_H * 4); // RGBA, starts transparent
function set(x, y, r, g, b) {
  const i = (y * ATLAS_W + x) * 4;
  px[i] = r;
  px[i + 1] = g;
  px[i + 2] = b;
  px[i + 3] = 255;
}
function grainPix(base, amt) {
  return [
    clamp(base[0] + (rng() * 2 - 1) * amt),
    clamp(base[1] + (rng() * 2 - 1) * amt),
    clamp(base[2] + (rng() * 2 - 1) * amt),
  ];
}
function mix(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function paintFur(rect, tail) {
  const [x1, y1, x2, y2] = rect;
  const nf = noiseField(6, 6);
  for (let y = y1; y < y2; y++) {
    for (let x = x1; x < x2; x++) {
      const u = (x - x1) / Math.max(1, x2 - x1 - 1);
      const v = (y - y1) / Math.max(1, y2 - y1 - 1);
      let base = mix(FUR, FUR_DARK, Math.pow(nf(u, v), 1.6) * 0.7);
      if (tail && v > 0.6) base = mix(base, FUR_DARK, (v - 0.6) / 0.4); // dark tip
      const [r, g, b] = grainPix(base, GRAIN_FUR);
      set(x, y, r, g, b);
    }
  }
}
function paintLeather(rect) {
  const [x1, y1, x2, y2] = rect;
  const nf = noiseField(5, 5);
  for (let y = y1; y < y2; y++) {
    for (let x = x1; x < x2; x++) {
      const u = (x - x1) / Math.max(1, x2 - x1 - 1);
      const v = (y - y1) / Math.max(1, y2 - y1 - 1);
      let base = mix(LEATHER, LEATHER_DARK, nf(u, v) * 0.45);
      // faint horizontal seams
      if (Math.abs(Math.sin(v * Math.PI * 3)) > 0.93) base = mix(base, LEATHER_DARK, 0.6);
      const [r, g, b] = grainPix(base, GRAIN_LEATHER);
      set(x, y, r, g, b);
    }
  }
}
function paintLimb(rect, lowerFrac) {
  const [x1, y1, x2, y2] = rect;
  const h = y2 - y1;
  const cut = y2 - Math.round(h * lowerFrac);
  paintLeather([x1, y1, x2, cut]);
  if (cut < y2) paintFur([x1, cut, x2, y2], false);
}

for (const el of elements) {
  for (const f of FACES) {
    const rect = el.uv[f];
    if (el.mat === "fur") paintFur(rect, false);
    else if (el.mat === "furTail") paintFur(rect, true);
    else if (el.mat === "leather") paintLeather(rect);
    else if (el.mat === "armLeather") {
      if (f === "down") paintFur(rect, false); // hand
      else if (f === "up") paintLeather(rect);
      else paintLimb(rect, 0.28);
    } else if (el.mat === "legLeather") {
      if (f === "down") paintFur(rect, false); // paw
      else if (f === "up") paintLeather(rect);
      else paintLimb(rect, 0.22);
    }
  }
}

// ── eyes: head north face, painted last ──
{
  const head = elements.find((e) => e.name === "head");
  const [x1, y1, x2] = head.uv.north;
  const y2 = head.uv.north[3];
  const w = x2 - x1;
  const hgt = y2 - y1;
  const ew = Math.max(2, Math.round(w * 0.22));
  const eh = Math.max(2, Math.round(hgt * 0.2));
  const ey = y1 + Math.round(hgt * 0.28);
  const exL = x1 + 1;
  const exR = x2 - 1 - ew;
  for (const ex of [exL, exR]) {
    for (let y = ey; y < ey + eh; y++) {
      for (let x = ex; x < ex + ew; x++) set(x, y, EYE[0], EYE[1], EYE[2]);
    }
  }
}

// ── opacity: neighbour-bleed then hard fill ──
function bleed(iterations) {
  for (let it = 0; it < iterations; it++) {
    let changed = 0;
    const copy = px.slice();
    for (let y = 0; y < ATLAS_H; y++) {
      for (let x = 0; x < ATLAS_W; x++) {
        const i = (y * ATLAS_W + x) * 4;
        if (copy[i + 3] === 255) continue;
        for (const [dx, dy] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ]) {
          const nx = x + dx;
          const ny = y + dy;
          if (nx < 0 || ny < 0 || nx >= ATLAS_W || ny >= ATLAS_H) continue;
          const j = (ny * ATLAS_W + nx) * 4;
          if (copy[j + 3] === 255) {
            set(x, y, copy[j], copy[j + 1], copy[j + 2]);
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
  if (px[p * 4 + 3] !== 255) {
    px[p * 4] = FUR_DARK[0];
    px[p * 4 + 1] = FUR_DARK[1];
    px[p * 4 + 2] = FUR_DARK[2];
    px[p * 4 + 3] = 255;
  }
}
for (let p = 0; p < ATLAS_W * ATLAS_H; p++) {
  if (px[p * 4 + 3] !== 255) throw new Error("transparent pixel survived");
}

// ── minimal RGBA PNG encoder ──
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
  const tb = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([tb, data])));
  return Buffer.concat([len, tb, data, crc]);
}
function encodePng(w, h, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const raw = Buffer.alloc(h * (w * 4 + 1));
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    rgba.subarray(y * w * 4, (y + 1) * w * 4).forEach((v, i) => {
      raw[y * (w * 4 + 1) + 1 + i] = v;
    });
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([
    sig,
    chunk("IHDR", ihdr),
    chunk("IDAT", idat),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
const pngB64 = encodePng(ATLAS_W, ATLAS_H, Buffer.from(px)).toString("base64");

// ── build bbmodel ──
const groupUuid = {};
for (const b of Object.keys(bones)) groupUuid[b] = randomUUID();
const elUuid = {};
for (const el of elements) elUuid[el.name] = randomUUID();

const bbElements = elements.map((el) => ({
  name: el.name,
  box_uv: false,
  render_order: "default",
  locked: false,
  export: true,
  scope: 0,
  allow_mirror_modeling: true,
  from: el.from,
  to: el.to,
  autouv: 0,
  color: 0,
  origin: [0, 0, 0],
  faces: Object.fromEntries(FACES.map((f) => [f, { uv: el.uv[f], texture: 0 }])),
  type: "cube",
  uuid: elUuid[el.name],
}));

const bbGroups = Object.entries(bones).map(([name, b]) => ({
  name,
  uuid: groupUuid[name],
  export: true,
  locked: false,
  scope: 0,
  selected: false,
  _static: { properties: {}, temp_data: {} },
  origin: b.origin,
  rotation: [0, 0, 0],
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

const childrenOf = (bone) =>
  elements.filter((e) => e.bone === bone).map((e) => elUuid[e.name]);
const outliner = [
  { uuid: groupUuid.body, isOpen: true, children: childrenOf("body") },
  {
    uuid: groupUuid.head,
    isOpen: true,
    children: childrenOf("head"),
  },
  { uuid: groupUuid.rightArm, isOpen: true, children: childrenOf("rightArm") },
  { uuid: groupUuid.leftArm, isOpen: true, children: childrenOf("leftArm") },
  { uuid: groupUuid.rightLeg, isOpen: true, children: childrenOf("rightLeg") },
  { uuid: groupUuid.leftLeg, isOpen: true, children: childrenOf("leftLeg") },
  { uuid: groupUuid.tail, isOpen: true, children: childrenOf("tail") },
];

const bbmodel = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "wolf_man",
  model_identifier: "",
  visible_box: [1, 1, 0],
  variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements: bbElements,
  groups: bbGroups,
  outliner,
  textures: [
    {
      name: "wolf_man",
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
      uuid: randomUUID(),
      source: `data:image/png;base64,${pngB64}`,
    },
  ],
};

writeFileSync(OUT, JSON.stringify(bbmodel));
console.log(`wrote ${OUT.pathname}  atlas ${ATLAS_W}x${ATLAS_H}  elements ${elements.length}`);
