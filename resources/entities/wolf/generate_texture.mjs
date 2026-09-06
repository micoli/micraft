// Paints resources/entities/wolf/wolf.bbmodel's texture atlas in place.
// Reads elements[].faces[].uv from the sibling wolf.bbmodel (disjoint per-face rects authored by
// build_geometry.mjs), paints procedural fur per body part, then base64-encodes the PNG back into
// textures[0].source. No PNG lib in repo -> hand-rolled RGBA codec on node:zlib.
// Run: make dc CMD="node resources/entities/wolf/generate_texture.mjs"
import { readFileSync, writeFileSync } from "node:fs";
import zlib from "node:zlib";

const BBMODEL = new URL("./wolf.bbmodel", import.meta.url);

// ── tunables ───────────────────────────────────────────────────────────────
const SEED = 0x5f0f;
const GRAIN = 11;             // per-channel jitter for fur
const MACRO_GRID = 8;         // value-noise resolution per element
const TAN_THRESHOLD = 0.64;   // noise above this -> tan patch

const COL = {
  furLight: [232, 232, 232],
  furGrey: [196, 196, 200],
  tan: [168, 152, 136],
  dark: [42, 42, 42],       // ears, nose
  muzzle: [216, 184, 144],
  throat: [154, 120, 96],
  paw: [242, 242, 242],
  eye: [216, 137, 43],      // amber
};

// element name -> material descriptor
const MATERIAL = {
  body: { base: "furLight", faceTint: { down: "throat", up: "furLight" } },
  head: { base: "furLight", faceTint: { down: "muzzle" } },
  snout: { base: "muzzle", faceTint: { north: "dark", down: "dark" } },
  earL: { base: "dark", faceTint: { south: "furGrey" } },
  earR: { base: "dark", faceTint: { south: "furGrey" } },
  tail: { base: "furLight" },
  rightArm: { base: "furGrey", faceTint: { down: "paw" } },
  leftArm: { base: "furGrey", faceTint: { down: "paw" } },
  rightLeg: { base: "furGrey", faceTint: { down: "paw" } },
  leftLeg: { base: "furGrey", faceTint: { down: "paw" } },
};

// ── seeded PRNG (mulberry32) ────────────────────────────────────────────────
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = mulberry32(SEED);
const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);
const lerp = (a, b, t) => a + (b - a) * t;
const mix = (c1, c2, t) => [lerp(c1[0], c2[0], t), lerp(c1[1], c2[1], t), lerp(c1[2], c2[2], t)];

// ── read model ─────────────────────────────────────────────────────────────
const model = JSON.parse(readFileSync(BBMODEL, "utf8"));
const W = model.resolution.width;
const H = model.resolution.height;
const px = new Uint8Array(W * H * 4); // all zero -> transparent

function set(x, y, r, g, b) {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const i = (y * W + x) * 4;
  px[i] = clamp(r); px[i + 1] = clamp(g); px[i + 2] = clamp(b); px[i + 3] = 255;
}

// per-element value-noise grid, bilinearly sampled
function makeNoise() {
  const g = [];
  for (let i = 0; i < (MACRO_GRID + 1) * (MACRO_GRID + 1); i++) g.push(rng());
  return (u, v) => {
    const gx = u * MACRO_GRID, gy = v * MACRO_GRID;
    const x0 = Math.min(MACRO_GRID, Math.floor(gx)), y0 = Math.min(MACRO_GRID, Math.floor(gy));
    const x1 = Math.min(MACRO_GRID, x0 + 1), y1 = Math.min(MACRO_GRID, y0 + 1);
    const fx = gx - x0, fy = gy - y0;
    const a = g[y0 * (MACRO_GRID + 1) + x0], b = g[y0 * (MACRO_GRID + 1) + x1];
    const c = g[y1 * (MACRO_GRID + 1) + x0], d = g[y1 * (MACRO_GRID + 1) + x1];
    return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
  };
}

function paintFace(rect, baseCol, tintCol, face) {
  const [x1, y1, x2, y2] = rect;
  const fw = x2 - x1, fh = y2 - y1;
  const noise = makeNoise();
  // cheap directional AO
  const shade = face === "up" ? 1.08 : face === "down" ? 0.86 : 1.0;
  for (let yy = 0; yy < fh; yy++) {
    for (let xx = 0; xx < fw; xx++) {
      const u = fw > 1 ? xx / (fw - 1) : 0.5;
      const v = fh > 1 ? yy / (fh - 1) : 0.5;
      const n = noise(u, v);
      let col = mix(COL[baseCol], COL.furGrey, Math.min(1, n * 0.55));
      if (n > TAN_THRESHOLD) col = mix(col, COL.tan, (n - TAN_THRESHOLD) / (1 - TAN_THRESHOLD) * 0.8);
      if (tintCol) col = mix(col, COL[tintCol], 0.75);
      const j = () => (rng() - 0.5) * 2 * GRAIN;
      set(x1 + xx, y1 + yy, (col[0] + j()) * shade, (col[1] + j()) * shade, (col[2] + j()) * shade);
    }
  }
}

for (const el of model.elements) {
  const m = MATERIAL[el.name];
  if (!m) throw new Error(`no material for element ${el.name}`);
  for (const [face, f] of Object.entries(el.faces)) {
    paintFace(f.uv, m.base, m.faceTint?.[face], face);
  }
}

// ── eyes: painted last on the head's forward (north) face ───────────────────
{
  const head = model.elements.find((e) => e.name === "head");
  const [x1, y1, x2, y2] = head.faces.north.uv;
  const fw = x2 - x1, fh = y2 - y1;
  const ew = Math.max(1, Math.round(fw * 0.18));
  const eh = Math.max(1, Math.round(fh * 0.18));
  const ey = y1 + Math.round(fh * 0.5);
  const inset = Math.max(1, Math.round(fw * 0.12));
  const drawEye = (ex) => {
    for (let yy = 0; yy < eh; yy++) for (let xx = 0; xx < ew; xx++) set(ex + xx, ey + yy, ...COL.eye);
  };
  drawEye(x1 + inset);
  drawEye(x2 - inset - ew);
}

// ── make canvas 100% opaque: iterative neighbour bleed, then hard fallback ──
function bleed(iterations) {
  for (let it = 0; it < iterations; it++) {
    let changed = 0;
    const copy = px.slice();
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const i = (y * W + x) * 4;
        if (copy[i + 3] === 255) continue;
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const nx = x + dx, ny = y + dy;
          if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
          const ni = (ny * W + nx) * 4;
          if (copy[ni + 3] === 255) {
            px[i] = copy[ni]; px[i + 1] = copy[ni + 1]; px[i + 2] = copy[ni + 2]; px[i + 3] = 255;
            changed++; break;
          }
        }
      }
    }
    if (!changed) break;
  }
}
bleed(Math.max(W, H));
for (let i = 0; i < px.length; i += 4) {
  if (px[i + 3] !== 255) { px[i] = COL.furGrey[0]; px[i + 1] = COL.furGrey[1]; px[i + 2] = COL.furGrey[2]; px[i + 3] = 255; }
}

// ── minimal RGBA PNG encoder on zlib ───────────────────────────────────────
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
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([t, data])), 0);
  return Buffer.concat([len, t, data, crc]);
}
function encodePng(width, height, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const raw = Buffer.alloc(height * (1 + width * 4));
  for (let y = 0; y < height; y++) {
    raw[y * (1 + width * 4)] = 0; // filter None
    rgba.subarray(y * width * 4, (y + 1) * width * 4).forEach((v, i) => {
      raw[y * (1 + width * 4) + 1 + i] = v;
    });
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}

const pngBuf = encodePng(W, H, Buffer.from(px));

// verify: every pixel opaque
for (let i = 3; i < px.length; i += 4) if (px[i] !== 255) throw new Error("transparent pixel remains");

model.textures[0].source = `data:image/png;base64,${pngBuf.toString("base64")}`;
writeFileSync(BBMODEL, JSON.stringify(model));
console.log(`painted wolf.bbmodel texture ${W}x${H}  (${pngBuf.length} bytes png)`);
