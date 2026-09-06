// Regenerates tiger.bbmodel (geometry UV packing + embedded texture). Re-runnable:
//   make dc CMD="node resources/entities/tiger/generate_texture.mjs"
// A big-cat tiger: quadruped rig, orange fur with black vertical stripes, off-white
// belly/chest/inner-legs/paws/cheeks, rounded ears, ringed raised tail, amber eyes,
// pink nose.
import zlib from "node:zlib";
import { writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";

const OUT = new URL("./tiger.bbmodel", import.meta.url);
const RNG_SEED = 20260908;
const ATLAS_W = 64;

// ── palette ──
const ORANGE = [230, 130, 55];
const ORANGE_DARK = [190, 100, 40];
const STRIPE = [40, 35, 38]; // near-black
const WHITE = [240, 238, 232];
const WHITE_DARK = [206, 204, 198];
const NOSE = [225, 150, 150]; // pink
const EYE = [200, 160, 50]; // amber
const EYE_PUPIL = [16, 14, 16];

const GRAIN_FUR = 12;
const GRAIN_WHITE = 8;
const STRIPE_FREQ = 0.9; // radians per texel along the stripe axis
const STRIPE_DUTY = 0.42; // fraction of each cycle that is black

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
function mix(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}
function grainPix(base, amt) {
  return [
    clamp(base[0] + (rng() * 2 - 1) * amt),
    clamp(base[1] + (rng() * 2 - 1) * amt),
    clamp(base[2] + (rng() * 2 - 1) * amt),
  ];
}
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
    return (
      g[y0 * gw + x0] * (1 - fx) * (1 - fy) +
      g[y0 * gw + x1] * fx * (1 - fy) +
      g[y1 * gw + x0] * (1 - fx) * fy +
      g[y1 * gw + x1] * fx * fy
    );
  };
}

// ── element table (pixels, 16px = 1 block, feet at y=0). "front" = -Z = north. ──
// mat drives painting; `stripePhase` offsets the stripe pattern so segments don't tile.
const elements = [
  { name: "body", from: [-5, 9, -11], to: [5, 19, 11], bone: "body", mat: "torso" },
  { name: "head", from: [-5, 12, -20], to: [4, 21, -11], bone: "head", mat: "head" },
  { name: "snout", from: [-3, 12, -24], to: [3, 17, -20], bone: "head", mat: "snout" },
  { name: "earL", from: [1, 21, -16], to: [4, 24, -13], bone: "head", mat: "ear" },
  { name: "earR", from: [-4, 21, -16], to: [-1, 24, -13], bone: "head", mat: "ear" },
  { name: "frontLegL", from: [-5, 0, -13], to: [-1, 12, -9], bone: "frontLegL", mat: "leg" },
  { name: "frontLegR", from: [1, 0, -13], to: [5, 12, -9], bone: "frontLegR", mat: "leg" },
  { name: "backLegL", from: [-5, 0, 6], to: [-1, 13, 10], bone: "backLegL", mat: "leg" },
  { name: "backLegR", from: [1, 0, 6], to: [5, 13, 10], bone: "backLegR", mat: "leg" },
  { name: "pawFL", from: [-5, 0, -14], to: [-1, 2, -9], bone: "frontLegL", mat: "paw" },
  { name: "pawFR", from: [1, 0, -14], to: [5, 2, -9], bone: "frontLegR", mat: "paw" },
  { name: "pawBL", from: [-5, 0, 5], to: [-1, 2, 10], bone: "backLegL", mat: "paw" },
  { name: "pawBR", from: [1, 0, 5], to: [5, 2, 10], bone: "backLegR", mat: "paw" },
  { name: "tailBase", from: [-2, 13, 11], to: [2, 17, 17], bone: "tail", mat: "tail", stripePhase: 0 },
  { name: "tailMid", from: [-2, 13, 17], to: [1, 16, 24], bone: "tail", mat: "tail", stripePhase: 6 },
  { name: "tailTip", from: [-1, 13, 24], to: [1, 15, 30], bone: "tail", mat: "tailTip", stripePhase: 13 },
];

const bones = {
  body: { origin: [0, 12, 0], rotation: [0, 0, 0] },
  head: { origin: [0, 15, -11], rotation: [0, 0, 0] },
  tail: { origin: [0, 15, 12], rotation: [-35, 0, 0] },
  frontLegL: { origin: [-3, 12, -11], rotation: [0, 0, 0] },
  frontLegR: { origin: [3, 12, -11], rotation: [0, 0, 0] },
  backLegL: { origin: [-3, 13, 8], rotation: [0, 0, 0] },
  backLegR: { origin: [3, 13, 8], rotation: [0, 0, 0] },
};

const FACES = ["north", "east", "south", "west", "up", "down"];
function faceSize(el, f) {
  const dx = el.to[0] - el.from[0];
  const dy = el.to[1] - el.from[1];
  const dz = el.to[2] - el.from[2];
  if (f === "north" || f === "south") return [dx, dy];
  if (f === "east" || f === "west") return [dz, dy];
  return [dx, dz];
}

// ── pack every face into a disjoint rect (1px gutter) ──
let cx = 1;
let cy = 1;
let rowH = 0;
let atlasH = 1;
for (const el of elements) {
  el.uv = {};
  for (const f of FACES) {
    const [w, h] = faceSize(el, f);
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
for (const el of elements) for (const f of FACES) rects.push([`${el.name}.${f}`, el.uv[f]]);
for (let i = 0; i < rects.length; i++) {
  for (let j = i + 1; j < rects.length; j++) {
    const a = rects[i][1];
    const b = rects[j][1];
    if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]) {
      throw new Error(`UV overlap ${rects[i][0]} vs ${rects[j][0]}`);
    }
  }
}

// ── canvas ──
const px = new Uint8Array(ATLAS_W * ATLAS_H * 4);
function set(x, y, r, g, b) {
  const i = (y * ATLAS_W + x) * 4;
  px[i] = r;
  px[i + 1] = g;
  px[i + 2] = b;
  px[i + 3] = 255;
}

// A stripe test: returns 0..1 stripe strength for a coordinate along the stripe axis.
function stripeAt(t) {
  const c = (Math.sin(t * STRIPE_FREQ) + 1) / 2; // 0..1
  return c < STRIPE_DUTY ? 1 : 0;
}

// Paint one face. opts: { base, white, stripes, stripeAxis: "u"|"v", stripePhase,
//   faceShade, wobserveWhiteBelly } — see callers.
function paintFace(rect, f, opts) {
  const [x1, y1, x2, y2] = rect;
  const nf = noiseField(6, 6);
  const white = opts.white ?? false;
  const baseCol = white ? WHITE : (opts.base ?? ORANGE);
  const darkCol = white ? WHITE_DARK : ORANGE_DARK;
  const grain = white ? GRAIN_WHITE : GRAIN_FUR;
  const faceShade = f === "up" ? -0.06 : f === "down" ? 0.12 : 0;
  for (let y = y1; y < y2; y++) {
    for (let x = x1; x < x2; x++) {
      const u = (x - x1) / Math.max(1, x2 - x1 - 1);
      const v = (y - y1) / Math.max(1, y2 - y1 - 1);
      let col = mix(baseCol, darkCol, Math.pow(nf(u, v), 1.6) * 0.5 + faceShade);
      // white belly creeping up the lower part of orange side faces
      if (!white && opts.bellyFrac && v > 1 - opts.bellyFrac) {
        const t = (v - (1 - opts.bellyFrac)) / opts.bellyFrac;
        col = mix(col, WHITE, Math.min(1, t * 1.4));
      }
      if (opts.stripes) {
        const along = opts.stripeAxis === "v" ? y - y1 : x - x1;
        if (stripeAt(along + (opts.stripePhase ?? 0)) > 0.5) col = mix(col, STRIPE, 0.82);
      }
      const [r, g, b] = grainPix(col, grain);
      set(x, y, r, g, b);
    }
  }
}

function paintElement(el) {
  for (const f of FACES) {
    const rect = el.uv[f];
    const dz = el.to[2] - el.from[2];
    switch (el.mat) {
      case "torso": {
        // sides striped along body length (Z -> u on east/west); up striped along X;
        // north (chest) & down (belly) white; south (rump) orange striped.
        if (f === "down" || f === "north") paintFace(rect, f, { white: true, stripes: f === "north", stripeAxis: "u" });
        else if (f === "up") paintFace(rect, f, { stripes: true, stripeAxis: "v" });
        else if (f === "south") paintFace(rect, f, { stripes: true, stripeAxis: "u" });
        else paintFace(rect, f, { stripes: true, stripeAxis: "u", bellyFrac: 0.4 }); // east/west
        break;
      }
      case "head": {
        // forehead (up) + sides striped; lower/front cheeks white; back (south) orange.
        if (f === "down") paintFace(rect, f, { white: true });
        else if (f === "north") paintFace(rect, f, { white: true, stripes: true, stripeAxis: "u", stripePhase: 3 });
        else if (f === "up") paintFace(rect, f, { stripes: true, stripeAxis: "v" });
        else paintFace(rect, f, { stripes: true, stripeAxis: "v", bellyFrac: 0.45 });
        break;
      }
      case "snout":
        if (f === "north") paintFace(rect, f, { base: NOSE });
        else if (f === "down" || f === "south") paintFace(rect, f, { white: true });
        else paintFace(rect, f, { white: true, stripes: true, stripeAxis: "v", stripePhase: 1 });
        break;
      case "ear":
        if (f === "north" || f === "down") paintFace(rect, f, { base: STRIPE });
        else paintFace(rect, f, { stripes: true, stripeAxis: "v" });
        break;
      case "leg":
        // outer faces orange striped, lower ~45% white (sock); inner faded white.
        if (f === "down") paintFace(rect, f, { white: true });
        else if (f === "up") paintFace(rect, f, { stripes: true, stripeAxis: "u" });
        else paintFace(rect, f, { stripes: true, stripeAxis: "v", stripePhase: 2, bellyFrac: 0.45 });
        break;
      case "paw":
        paintFace(rect, f, { white: true });
        break;
      case "tail": {
        // rings along the tail length (Z). On east/west that is u; on up/down that is v.
        const axis = f === "up" || f === "down" ? "v" : f === "north" || f === "south" ? "u" : "u";
        paintFace(rect, f, { stripes: true, stripeAxis: axis, stripePhase: el.stripePhase, base: ORANGE });
        // north/south cross-sections: bias whole face by ring parity for a clean band
        if (f === "north" || f === "south") {
          if (stripeAt((el.stripePhase ?? 0) + dz) > 0.5) paintFace(rect, f, { base: STRIPE });
        }
        break;
      }
      case "tailTip":
        paintFace(rect, f, { base: STRIPE });
        break;
    }
  }
}

for (const el of elements) paintElement(el);

// ── eyes: head north face, painted last ──
{
  const head = elements.find((e) => e.name === "head");
  const [x1, y1, x2, y2] = head.uv.north;
  const w = x2 - x1;
  const hgt = y2 - y1;
  const ew = Math.max(2, Math.round(w * 0.2));
  const eh = Math.max(2, Math.round(hgt * 0.2));
  const ey = y1 + Math.round(hgt * 0.22);
  const exL = x1 + 1;
  const exR = x2 - 1 - ew;
  for (const ex of [exL, exR]) {
    for (let y = ey; y < ey + eh; y++) {
      for (let x = ex; x < ex + ew; x++) set(x, y, EYE[0], EYE[1], EYE[2]);
    }
    const pxc = ex + ((ew / 2) | 0);
    for (let y = ey; y < ey + eh; y++) set(pxc, y, EYE_PUPIL[0], EYE_PUPIL[1], EYE_PUPIL[2]);
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
    px[p * 4] = ORANGE_DARK[0];
    px[p * 4 + 1] = ORANGE_DARK[1];
    px[p * 4 + 2] = ORANGE_DARK[2];
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
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
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
  rotation: b.rotation ?? [0, 0, 0],
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

const childrenOf = (bone) => elements.filter((e) => e.bone === bone).map((e) => elUuid[e.name]);
const outliner = Object.keys(bones).map((b) => ({
  uuid: groupUuid[b],
  isOpen: true,
  children: childrenOf(b),
}));

const bbmodel = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "tiger",
  model_identifier: "",
  visible_box: [1, 1, 0],
  variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements: bbElements,
  groups: bbGroups,
  outliner,
  textures: [
    {
      name: "tiger",
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
