// Regenerable generator for the `deer_baby` NPC: builds geometry (<type>.bbmodel) and paints
// its texture procedurally. Re-run after tweaking the constants below to regenerate without
// redoing UV math by hand. Run in-container:
//   make dc CMD="node resources/entities/deer_baby/generate_texture.mjs"
import zlib from "node:zlib";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

// ── Tunables ────────────────────────────────────────────────────────────────
const NAME = "deer_baby";
const SEED = 0xfa17b0;
const DENSITY = 2; // texels per model pixel
const ATLAS_W = 128;

const COL = {
  fawn: [201, 168, 120],
  cream: [232, 221, 193],
  headBrown: [104, 72, 46],
  nose: [26, 21, 18],
  hoof: [24, 19, 16],
  spot: [242, 234, 219],
  earInner: [223, 208, 183],
  eye: [12, 9, 7],
};

// Fawn proportions derived from the reworked adult `deer.bbmodel` (same slender build,
// same body>neck>head>ears hierarchy, forward-tilted neck+head) scaled ~0.65 with the
// baby's larger head-to-body ratio. Bone tilt lives on the bones, not baked into elements.
// name -> [fx,fy,fz, tx,ty,tz, part]
const PARTS = {
  body: [-2, 6, -4, 2, 11, 4, "body"],
  neck: [-1, 9, -5, 1, 14, -3, "neck"],
  head: [-1.5, 13, -7, 1.5, 16, -3, "head"],
  earL: [-3, 16, -6, -1, 18, -5, "ear"],
  earR: [1, 16, -6, 3, 18, -5, "ear"],
  tail: [-1, 8, 4, 1, 10, 6, "tail"],
  legFL: [-3, 0, -4, -1, 6, -2, "leg"],
  legFR: [1, 0, -4, 3, 6, -2, "leg"],
  legBL: [-3, 0, 2, -1, 6, 4, "leg"],
  legBR: [1, 0, 2, 3, 6, 4, "leg"],
};

// bone -> {origin, rotation, children:[elementNames], parent}
const BONES = {
  body: { origin: [0, 8, 0], rotation: [0, 0, 0], elems: ["body"] },
  neck: { origin: [0, 10, -3], rotation: [-17.5, 0, 0], elems: ["neck"], parent: "body" },
  head: { origin: [0, 14, -5], rotation: [-22.5, 0, 0], elems: ["head"], parent: "neck" },
  earL: { origin: [-1.5, 16, -5], rotation: [0, 0, -20], elems: ["earL"], parent: "head" },
  earR: { origin: [1.5, 16, -5], rotation: [0, 0, 20], elems: ["earR"], parent: "head" },
  tail: { origin: [0, 9, 4], rotation: [-25, 0, 0], elems: ["tail"], parent: "body" },
  frontLegL: { origin: [-2, 6, -3], rotation: [0, 0, 0], elems: ["legFL"], parent: "body" },
  frontLegR: { origin: [2, 6, -3], rotation: [0, 0, 0], elems: ["legFR"], parent: "body" },
  backLegL: { origin: [-2, 6, 3], rotation: [0, 0, 0], elems: ["legBL"], parent: "body" },
  backLegR: { origin: [2, 6, 3], rotation: [0, 0, 0], elems: ["legBR"], parent: "body" },
};

// ── Helpers ─────────────────────────────────────────────────────────────────
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
const uuid = () =>
  "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (rnd() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });

const FACE_ORDER = ["north", "east", "south", "west", "up", "down"];
function faceDims(sx, sy, sz, f) {
  if (f === "north" || f === "south") return [sx, sy];
  if (f === "east" || f === "west") return [sz, sy];
  return [sx, sz]; // up / down
}

// ── Geometry + UV packing ───────────────────────────────────────────────────
function buildElements() {
  const elements = [];
  let cx = 0;
  let cy = 0;
  let rowH = 0;
  const place = (w, h) => {
    if (cx + w > ATLAS_W) {
      cx = 0;
      cy += rowH + 1;
      rowH = 0;
    }
    const rect = [cx, cy, cx + w, cy + h];
    cx += w + 1;
    rowH = Math.max(rowH, h);
    return rect;
  };
  for (const [name, p] of Object.entries(PARTS)) {
    const [fx, fy, fz, tx, ty, tz, part] = p;
    const sx = (tx - fx) * DENSITY;
    const sy = (ty - fy) * DENSITY;
    const sz = (tz - fz) * DENSITY;
    const faces = {};
    for (const f of FACE_ORDER) {
      const [w, h] = faceDims(sx, sy, sz, f);
      faces[f] = { uv: place(w, h), texture: 0 };
    }
    elements.push({
      name,
      box_uv: false,
      render_order: "default",
      locked: false,
      export: true,
      allow_mirror_modeling: false,
      from: [fx, fy, fz],
      to: [tx, ty, tz],
      autouv: 0,
      color: 0,
      origin: [0, 0, 0],
      faces,
      type: "cube",
      uuid: uuid(),
      _part: part,
    });
  }
  const atlasH = cy + rowH + ((cy + rowH) % 2);
  return { elements, atlasH: Math.max(atlasH, 16) };
}

function buildModel() {
  const { elements, atlasH } = buildElements();
  const byName = Object.fromEntries(elements.map((e) => [e.name, e]));
  const groups = [];
  const groupUuid = {};
  for (const b of Object.keys(BONES)) groupUuid[b] = uuid();
  for (const [b, cfg] of Object.entries(BONES)) {
    groups.push({
      name: b,
      uuid: groupUuid[b],
      export: true,
      locked: false,
      origin: cfg.origin,
      rotation: cfg.rotation,
      color: 0,
      children: [],
      isOpen: true,
    });
  }
  const nodeFor = (b) => ({
    uuid: groupUuid[b],
    isOpen: true,
    children: [
      ...BONES[b].elems.map((n) => byName[n].uuid),
      ...Object.keys(BONES)
        .filter((c) => BONES[c].parent === b)
        .map(nodeFor),
    ],
  });
  const outliner = Object.keys(BONES)
    .filter((b) => !BONES[b].parent)
    .map(nodeFor);

  return {
    meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
    name: NAME,
    resolution: { width: ATLAS_W, height: atlasH },
    elements: elements.map(({ _part, ...e }) => e),
    groups,
    outliner,
    textures: [
      {
        name: NAME,
        id: "0",
        width: ATLAS_W,
        height: atlasH,
        uv_width: ATLAS_W,
        uv_height: atlasH,
        internal: true,
        saved: false,
        file_format: "png",
        render_mode: "default",
        visible: true,
        uuid: uuid(),
        source: "data:image/png;base64,",
      },
    ],
    _parts: Object.fromEntries(elements.map((e) => [e.name, e._part])),
  };
}

// ── Texture painting ────────────────────────────────────────────────────────
function jitter([r, g, b], amt) {
  const j = () => Math.round((rnd() * 2 - 1) * amt);
  return [clamp(r + j()), clamp(g + j()), clamp(b + j())];
}
const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v);
const lerp = (a, b, t) => a + (b - a) * t;
const mix = (a, c, t) => [
  clamp(lerp(a[0], c[0], t)),
  clamp(lerp(a[1], c[1], t)),
  clamp(lerp(a[2], c[2], t)),
];

function noiseField() {
  const N = 8;
  const g = [];
  for (let i = 0; i < N * N; i++) g.push(rnd());
  return (u, v) => {
    const x = u * (N - 1);
    const y = v * (N - 1);
    const x0 = Math.floor(x);
    const y0 = Math.floor(y);
    const x1 = Math.min(x0 + 1, N - 1);
    const y1 = Math.min(y0 + 1, N - 1);
    const tx = x - x0;
    const ty = y - y0;
    const a = lerp(g[y0 * N + x0], g[y0 * N + x1], tx);
    const b = lerp(g[y1 * N + x0], g[y1 * N + x1], tx);
    return lerp(a, b, ty);
  };
}

function paint(model) {
  const W = model.resolution.width;
  const H = model.resolution.height;
  const buf = new Uint8Array(W * H * 4); // RGBA, all zero => transparent
  const setPx = (x, y, [r, g, b]) => {
    const o = (y * W + x) * 4;
    buf[o] = r;
    buf[o + 1] = g;
    buf[o + 2] = b;
    buf[o + 3] = 255;
  };

  for (const el of model.elements) {
    const part = model._parts[el.name];
    for (const f of FACE_ORDER) {
      const [x1, y1, x2, y2] = el.faces[f].uv;
      const w = x2 - x1;
      const h = y2 - y1;
      const nf = noiseField();
      for (let yy = 0; yy < h; yy++) {
        for (let xx = 0; xx < w; xx++) {
          const u = w > 1 ? xx / (w - 1) : 0;
          const v = h > 1 ? yy / (h - 1) : 0;
          setPx(x1 + xx, y1 + yy, faceColor(part, f, u, v, nf));
        }
      }
    }
  }

  // Eyes last — on the head element's north (front) face.
  const head = model.elements.find((e) => e.name === "head");
  {
    const [hx1, hy1, hx2] = head.faces.north.uv;
    const w = hx2 - hx1;
    const eyeW = Math.max(2, Math.round(w * 0.22));
    const eyeY = hy1 + Math.round((head.faces.north.uv[3] - hy1) * 0.3);
    const lx = hx1 + 1;
    const rx = hx2 - 1 - eyeW;
    for (let i = 0; i < eyeW; i++) {
      for (let j = 0; j < eyeW; j++) {
        setPx(lx + i, eyeY + j, COL.eye);
        setPx(rx + i, eyeY + j, COL.eye);
      }
    }
  }

  bleedOpaque(buf, W, H);
  verifyOpaque(buf, W, H);
  return { buf, W, H };
}

function faceColor(part, f, u, v, nf) {
  const shade = f === "up" ? 1.08 : f === "down" ? 0.86 : 1.0;
  const sh = (c) => c.map((x) => clamp(x * shade));

  if (part === "leg") {
    let base = COL.fawn;
    if (v > 0.66) base = COL.cream;
    if (f === "down" || v > 0.9) base = COL.hoof;
    return jitter(sh(base), base === COL.hoof ? 4 : 9);
  }
  if (part === "ear") {
    const edge = u < 0.25 || u > 0.75 || v < 0.2 || v > 0.8;
    return jitter(edge ? COL.headBrown : COL.earInner, 8);
  }
  if (part === "head") {
    let base = COL.headBrown;
    if (f === "north" && v > 0.72) base = COL.nose;
    if (f === "down") base = COL.nose;
    return jitter(sh(base), base === COL.nose ? 4 : 10);
  }
  if (part === "tail") {
    const white = f === "down" || f === "south";
    return jitter(white ? COL.spot : sh(COL.fawn), 8);
  }
  // body + neck : fawn with cream belly + white spots
  if (f === "down") return jitter(sh(COL.cream), 8);
  let c = jitter(sh(COL.fawn), 11);
  const n = nf(u, v);
  const spotThresh = part === "neck" ? 0.72 : 0.6;
  if (n > spotThresh && f !== "up") {
    c = mix(c, COL.spot, Math.min(1, (n - spotThresh) * 3) * 0.9);
  }
  return c.map(clamp);
}

function bleedOpaque(buf, W, H) {
  const idx = (x, y) => (y * W + x) * 4;
  for (let pass = 0; pass < 64; pass++) {
    let changed = 0;
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const o = idx(x, y);
        if (buf[o + 3] === 255) continue;
        for (const [dx, dy] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ]) {
          const nx = x + dx;
          const ny = y + dy;
          if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
          const no = idx(nx, ny);
          if (buf[no + 3] === 255) {
            buf[o] = buf[no];
            buf[o + 1] = buf[no + 1];
            buf[o + 2] = buf[no + 2];
            buf[o + 3] = 255;
            changed++;
            break;
          }
        }
      }
    }
    if (!changed) break;
  }
  for (let i = 0; i < W * H; i++) {
    if (buf[i * 4 + 3] !== 255) {
      buf[i * 4] = COL.fawn[0];
      buf[i * 4 + 1] = COL.fawn[1];
      buf[i * 4 + 2] = COL.fawn[2];
      buf[i * 4 + 3] = 255;
    }
  }
}

function verifyOpaque(buf, W, H) {
  for (let i = 0; i < W * H; i++) {
    if (buf[i * 4 + 3] !== 255) throw new Error(`transparent pixel at ${i % W},${(i / W) | 0}`);
  }
}

// ── PNG codec (RGBA, 8-bit) ─────────────────────────────────────────────────
const CRC_TABLE = (() => {
  const t = [];
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
function encodePng(buf, W, H) {
  const raw = Buffer.alloc(H * (1 + W * 4));
  for (let y = 0; y < H; y++) {
    raw[y * (1 + W * 4)] = 0;
    Buffer.from(buf.buffer, y * W * 4, W * 4).copy(raw, y * (1 + W * 4) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0);
  ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([
    sig,
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
function decodePng(png) {
  let p = 8;
  let W = 0;
  let H = 0;
  const idat = [];
  while (p < png.length) {
    const len = png.readUInt32BE(p);
    const type = png.toString("ascii", p + 4, p + 8);
    const data = png.subarray(p + 8, p + 8 + len);
    if (type === "IHDR") {
      W = data.readUInt32BE(0);
      H = data.readUInt32BE(4);
    } else if (type === "IDAT") idat.push(data);
    else if (type === "IEND") break;
    p += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const out = Buffer.alloc(W * H * 4);
  const stride = W * 4;
  const paeth = (a, b, c) => {
    const pp = a + b - c;
    const pa = Math.abs(pp - a);
    const pb = Math.abs(pp - b);
    const pc = Math.abs(pp - c);
    return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
  };
  for (let y = 0; y < H; y++) {
    const ft = raw[y * (stride + 1)];
    const row = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= 4 ? out[y * stride + i - 4] : 0;
      const b = y > 0 ? out[(y - 1) * stride + i] : 0;
      const c = y > 0 && i >= 4 ? out[(y - 1) * stride + i - 4] : 0;
      let val = row[i];
      if (ft === 1) val += a;
      else if (ft === 2) val += b;
      else if (ft === 3) val += (a + b) >> 1;
      else if (ft === 4) val += paeth(a, b, c);
      out[y * stride + i] = val & 0xff;
    }
  }
  return { W, H, data: out };
}

// ── Run ─────────────────────────────────────────────────────────────────────
const dir = dirname(fileURLToPath(import.meta.url));
const bbPath = join(dir, `${NAME}.bbmodel`);

const model = buildModel();
// no-overlap check
const rects = [];
for (const el of model.elements)
  for (const f of FACE_ORDER) rects.push([el.name, f, el.faces[f].uv]);
for (let i = 0; i < rects.length; i++)
  for (let j = i + 1; j < rects.length; j++) {
    const [a1, b1, c1, d1] = rects[i][2];
    const [a2, b2, c2, d2] = rects[j][2];
    if (a1 < c2 && a2 < c1 && b1 < d2 && b2 < d1)
      throw new Error(`UV overlap ${rects[i][0]}.${rects[i][1]} vs ${rects[j][0]}.${rects[j][1]}`);
  }
for (const el of model.elements)
  for (const f of FACE_ORDER)
    for (const n of el.faces[f].uv)
      if (!Number.isInteger(n)) throw new Error(`non-integer uv on ${el.name}.${f}`);

const { _parts, ...clean } = model;
model._parts = _parts;
const { buf, W, H } = paint(model);
const png = encodePng(buf, W, H);

// read-back verify
const dec = decodePng(png);
for (let i = 0; i < dec.W * dec.H; i++)
  if (dec.data[i * 4 + 3] !== 255) throw new Error("decoded PNG has transparency");

clean.textures[0].source = "data:image/png;base64," + png.toString("base64");
writeFileSync(bbPath, JSON.stringify(clean));
console.log(`wrote ${NAME}.bbmodel  atlas ${W}x${H}  elements ${clean.elements.length}`);
