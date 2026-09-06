// Generates resources/entities/cat/cat.bbmodel — geometry, per-face UV packing and
// procedural fur texture for the low-poly domestic cat NPC.
// Run: make dc CMD="node resources/entities/cat/generate_texture.mjs"
import { readFileSync, writeFileSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const TYPE = "cat";
const SEED = 0x5eed_ca7;
const ATLAS_W = 64;

// ---- palette --------------------------------------------------------------
const C = {
  fur: [125, 128, 136], // dominant grey
  furDark: [74, 77, 84], // belly / shadow
  furLight: [160, 163, 170], // back highlight / mottle accent
  white: [223, 226, 230], // muzzle, chest, paws
  ear: [214, 138, 154], // inner-ear pink
  eye: [185, 194, 74], // yellow-green
  nose: [201, 120, 130],
};

// ---- geometry ------------------------------------------------------------
// region drives which colour + material treatment each element gets.
const CFG = globalThis.__CAT_CFG__ ?? {
  type: TYPE,
  seed: SEED,
  file: join(HERE, `${TYPE}.bbmodel`),
  tailRot: -62,
  elements: [
    { name: "body", from: [-3, 6, -6], to: [3, 11, 6], bone: "body", region: "body" },
    { name: "head", from: [-2.5, 9, -10], to: [2.5, 14, -6], bone: "head", region: "head" },
    { name: "earL", from: [1, 13.5, -9], to: [3, 16, -8], bone: "head", region: "ear" },
    { name: "earR", from: [-3, 13.5, -9], to: [-1, 16, -8], bone: "head", region: "ear" },
    { name: "tail", from: [-1, 10, 5], to: [1, 20, 7], bone: "tail", region: "tail" },
    { name: "frontLegL", from: [-3, 0, -5], to: [-1, 7, -3], bone: "frontLegL", region: "leg" },
    { name: "frontLegR", from: [1, 0, -5], to: [3, 7, -3], bone: "frontLegR", region: "leg" },
    { name: "backLegL", from: [-3, 0, 3], to: [-1, 7, 5], bone: "backLegL", region: "leg" },
    { name: "backLegR", from: [1, 0, 3], to: [3, 7, 5], bone: "backLegR", region: "leg" },
  ],
  bones: [
    { name: "body", origin: [0, 8, 0], rotation: [0, 0, 0], root: true },
    { name: "head", origin: [0, 11, -6], rotation: [0, 0, 0], parent: "body" },
    { name: "tail", origin: [0, 11, 5], rotation: [-62, 0, 0], parent: "body" },
    { name: "frontLegL", origin: [-2, 7, -4], rotation: [0, 0, 0], parent: "body" },
    { name: "frontLegR", origin: [2, 7, -4], rotation: [0, 0, 0], parent: "body" },
    { name: "backLegL", origin: [-2, 7, 4], rotation: [0, 0, 0], parent: "body" },
    { name: "backLegR", origin: [2, 7, 4], rotation: [0, 0, 0], parent: "body" },
  ],
};

// ---- PRNG (mulberry32) --------------------------------------------------
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function uuid(rng) {
  const h = [];
  for (let i = 0; i < 16; i++) h.push(Math.floor(rng() * 256));
  h[6] = (h[6] & 0x0f) | 0x40;
  h[8] = (h[8] & 0x3f) | 0x80;
  const s = h.map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`;
}

// ---- UV packing -------------------------------------------------------
function faceDims(el) {
  const dx = el.to[0] - el.from[0];
  const dy = el.to[1] - el.from[1];
  const dz = el.to[2] - el.from[2];
  return {
    north: [dx, dy],
    south: [dx, dy],
    east: [dz, dy],
    west: [dz, dy],
    up: [dx, dz],
    down: [dx, dz],
  };
}

function packUV(elements) {
  let cx = 0,
    cy = 0,
    rowH = 0;
  const order = ["north", "east", "south", "west", "up", "down"];
  for (const el of elements) {
    const dims = faceDims(el);
    el.faces = {};
    for (const f of order) {
      let [w, h] = dims[f];
      w = Math.round(w);
      h = Math.round(h);
      if (w <= 0 || h <= 0) throw new Error(`bad face ${el.name}.${f}`);
      if (cx + w > ATLAS_W) {
        cx = 0;
        cy += rowH;
        rowH = 0;
      }
      el.faces[f] = { uv: [cx, cy, cx + w, cy + h], texture: 0 };
      cx += w;
      rowH = Math.max(rowH, h);
    }
  }
  return cy + rowH;
}

// ---- texture painting ------------------------------------------------
function build() {
  const rng = mulberry32(CFG.seed);
  const elements = CFG.elements.map((e) => ({ ...e }));
  const usedH = packUV(elements);
  const atlasH = Math.max(1, usedH);

  // no-overlap check
  const rects = [];
  for (const el of elements)
    for (const f of Object.keys(el.faces)) rects.push([el.name, f, el.faces[f].uv]);
  for (let i = 0; i < rects.length; i++)
    for (let j = i + 1; j < rects.length; j++) {
      const [an, af, a] = rects[i];
      const [bn, bf, b] = rects[j];
      if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3])
        throw new Error(`UV overlap ${an}.${af} vs ${bn}.${bf}`);
    }

  const W = ATLAS_W;
  const H = atlasH;
  const px = new Uint8Array(W * H * 4); // all transparent initially

  const set = (x, y, [r, g, b]) => {
    const o = (y * W + x) * 4;
    px[o] = r;
    px[o + 1] = g;
    px[o + 2] = b;
    px[o + 3] = 255;
  };
  const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);
  const jitter = (c, amt) => c.map((ch) => clamp(ch + (rng() * 2 - 1) * amt));
  const mix = (a, b, t) => a.map((ch, i) => clamp(ch + (b[i] - ch) * t));

  // coarse value-noise grid per element for mottle
  function noiseField(n) {
    const g = [];
    for (let i = 0; i < n * n; i++) g.push(rng());
    return (u, v) => {
      const fx = u * (n - 1),
        fy = v * (n - 1);
      const x0 = Math.floor(fx),
        y0 = Math.floor(fy);
      const x1 = Math.min(n - 1, x0 + 1),
        y1 = Math.min(n - 1, y0 + 1);
      const tx = fx - x0,
        ty = fy - y0;
      const a = g[y0 * n + x0],
        b = g[y0 * n + x1],
        c = g[y1 * n + x0],
        d = g[y1 * n + x1];
      return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
    };
  }

  for (const el of elements) {
    for (const [fname, face] of Object.entries(el.faces)) {
      const [x1, y1, x2, y2] = face.uv;
      const fw = x2 - x1,
        fh = y2 - y1;
      const nf = noiseField(4);
      const grainAmt = el.region === "ear" ? 8 : 11;

      for (let yy = 0; yy < fh; yy++) {
        for (let xx = 0; xx < fw; xx++) {
          const u = fw > 1 ? xx / (fw - 1) : 0;
          const v = fh > 1 ? yy / (fh - 1) : 0;
          let base = C.fur;

          if (el.region === "body") {
            base = fname === "down" ? C.furDark : C.fur;
            if (fname === "south") base = mix(C.white, C.fur, 0.35); // chest hint at front-bottom
          } else if (el.region === "head") {
            base = C.fur;
            if (fname === "north" && v > 0.5) base = mix(C.white, C.fur, 0.15 + (v - 0.5)); // muzzle
            if (fname === "down") base = C.white;
          } else if (el.region === "ear") {
            base = fname === "north" ? C.ear : C.fur;
          } else if (el.region === "tail") {
            base = v > 0.8 ? C.furDark : C.fur; // dark tip
          } else if (el.region === "leg") {
            base = v > 0.62 ? C.white : C.fur; // white paw / sock
          }

          // macro mottle towards furLight on upper body/head
          if ((el.region === "body" || el.region === "head") && fname !== "down") {
            const n = nf(u, v);
            if (n > 0.62) base = mix(base, C.furLight, (n - 0.62) * 1.6);
          }
          // cheap AO: up lighter, down/belly darker
          if (fname === "up") base = mix(base, C.furLight, 0.18);
          if (fname === "down" && el.region !== "head") base = mix(base, C.furDark, 0.25);

          set(x1 + xx, y1 + yy, jitter(base, grainAmt));
        }
      }
    }
  }

  // ---- eyes + nose on head north face (painted last) ----
  const head = elements.find((e) => e.name === "head");
  {
    const [hx1, hy1, hx2, hy2] = head.faces.north.uv;
    const fw = hx2 - hx1,
      fh = hy2 - hy1;
    const eyeW = Math.max(1, Math.round(fw * 0.16));
    const eyeH = Math.max(1, Math.round(fh * 0.16));
    const eyeY = hy1 + Math.round(fh * 0.32);
    const insetL = hx1 + Math.round(fw * 0.16);
    const insetR = hx2 - Math.round(fw * 0.16) - eyeW;
    for (const ex of [insetL, insetR])
      for (let dy = 0; dy < eyeH; dy++)
        for (let dx = 0; dx < eyeW; dx++) set(ex + dx, eyeY + dy, C.eye);
    // nose: small pink dot centred, lower
    const nx = hx1 + Math.round(fw / 2) - 1;
    const ny = hy1 + Math.round(fh * 0.7);
    for (let dy = 0; dy < 2; dy++)
      for (let dx = 0; dx < 2; dx++) set(nx + dx, ny + dy, C.nose);
  }

  // ---- opacity: neighbour bleed then fallback fill ----
  const isOpaque = (x, y) => px[(y * W + x) * 4 + 3] === 255;
  for (let iter = 0; iter < 64; iter++) {
    let changed = false;
    const snap = px.slice();
    for (let y = 0; y < H; y++)
      for (let x = 0; x < W; x++) {
        if (snap[(y * W + x) * 4 + 3] === 255) continue;
        for (const [dx, dy] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ]) {
          const nx = x + dx,
            ny = y + dy;
          if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
          if (snap[(ny * W + nx) * 4 + 3] === 255) {
            const o = (ny * W + nx) * 4;
            set(x, y, [snap[o], snap[o + 1], snap[o + 2]]);
            changed = true;
            break;
          }
        }
      }
    if (!changed) break;
  }
  for (let y = 0; y < H; y++)
    for (let x = 0; x < W; x++) if (!isOpaque(x, y)) set(x, y, C.furDark);

  // verify fully opaque
  for (let i = 3; i < px.length; i += 4)
    if (px[i] !== 255) throw new Error("transparent pixel remains");

  // ---- PNG encode (RGBA, filter 0) ----
  const src = encodePNG(px, W, H);

  // ---- assemble bbmodel ----
  const groupUuid = {};
  for (const b of CFG.bones) groupUuid[b.name] = uuid(rng);
  const elUuid = {};
  for (const el of elements) elUuid[el.name] = uuid(rng);

  const outElements = elements.map((el) => ({
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
    faces: Object.fromEntries(
      Object.entries(el.faces).map(([k, v]) => [k, { uv: v.uv, texture: 0 }]),
    ),
    type: "cube",
    uuid: elUuid[el.name],
  }));

  const groups = CFG.bones.map((b) => ({
    name: b.name,
    uuid: groupUuid[b.name],
    export: true,
    locked: false,
    origin: b.origin,
    rotation: b.rotation,
    color: 0,
    children: [],
    isOpen: true,
  }));

  // outliner tree
  const nodeFor = (boneName) => {
    const kidsBones = CFG.bones.filter((b) => b.parent === boneName);
    const kidsEls = elements.filter((e) => e.bone === boneName).map((e) => elUuid[e.name]);
    return {
      uuid: groupUuid[boneName],
      isOpen: true,
      children: [...kidsEls, ...kidsBones.map((b) => nodeFor(b.name))],
    };
  };
  const outliner = CFG.bones.filter((b) => b.root).map((b) => nodeFor(b.name));

  const bbmodel = {
    meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
    name: CFG.type,
    resolution: { width: W, height: H },
    elements: outElements,
    groups,
    outliner,
    textures: [
      {
        name: CFG.type,
        id: "0",
        width: W,
        height: H,
        uv_width: W,
        uv_height: H,
        internal: true,
        saved: false,
        source: src,
      },
    ],
  };

  writeFileSync(CFG.file, JSON.stringify(bbmodel));
  console.log(`wrote ${CFG.file}  (${W}x${H} atlas, ${elements.length} elements)`);
}

// ---- minimal RGBA PNG encoder ----
function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}
function chunk(type, data) {
  const t = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function encodePNG(rgba, w, h) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
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
  const idat = deflateSync(raw);
  return (
    "data:image/png;base64," +
    Buffer.concat([
      sig,
      chunk("IHDR", ihdr),
      chunk("IDAT", idat),
      chunk("IEND", Buffer.alloc(0)),
    ]).toString("base64")
  );
}

build();
