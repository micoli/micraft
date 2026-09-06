// Paints resources/entities/elephant/elephant.bbmodel's embedded texture atlas.
// Re-runnable: reads the sibling bbmodel's per-face UV rects, paints procedural
// wrinkled-grey elephant skin + ivory tusks + eyes, writes the PNG back into textures[0].source.
// Run: make dc CMD="node resources/entities/elephant/generate_texture.mjs"
import { readFileSync, writeFileSync } from "node:fs";
import zlib from "node:zlib";

// ---- tunables -------------------------------------------------------------
const SEED = 0x1e1efa47;
const COLORS = {
  body: [122, 122, 128],
  head: [120, 120, 126],
  leg: [110, 110, 116],
  trunk: [116, 116, 122],
  ear: [104, 104, 112],
  tusk: [232, 226, 205],
  eye: [40, 28, 20],
  wrinkle: [92, 92, 100], // accent for macro pattern
};
const GRAIN = 9; // +/- per-channel jitter (smooth skin -> tight)
const WRINKLE_MIX = 0.35;
// ------------------------------------------------------------------------

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rnd = mulberry32(SEED);
const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);

const url = new URL("./elephant.bbmodel", import.meta.url);
const model = JSON.parse(readFileSync(url, "utf8"));
const W = model.resolution.width;
const H = model.resolution.height;
const px = new Uint8Array(W * H * 4); // all zero => transparent

function set(x, y, r, g, b) {
  const i = (y * W + x) * 4;
  px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = 255;
}

// coarse value-noise grid per element for wrinkle macro pattern
function noiseField(n) {
  const g = new Float64Array(n * n);
  for (let i = 0; i < g.length; i++) g[i] = rnd();
  return (u, v) => {
    const x = u * (n - 1), y = v * (n - 1);
    const x0 = Math.floor(x), y0 = Math.floor(y);
    const x1 = Math.min(x0 + 1, n - 1), y1 = Math.min(y0 + 1, n - 1);
    const fx = x - x0, fy = y - y0;
    const a = g[y0 * n + x0], b = g[y0 * n + x1], c = g[y1 * n + x0], d = g[y1 * n + x1];
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy;
  };
}

const MAT_BY_ELEMENT = {
  body: "body", hump: "body", tail: "body", head: "head",
  frontLegL: "leg", frontLegR: "leg", backLegL: "leg", backLegR: "leg",
  earL: "ear", earR: "ear", tuskL: "tusk", tuskR: "tusk",
  trunk0: "trunk", trunk1: "trunk", trunk2: "trunk", trunk3: "trunk",
};
const FACE_SHADE = { up: 12, down: -14, north: 4, south: -6, east: -2, west: -2 };

for (const el of model.elements) {
  const mat = MAT_BY_ELEMENT[el.name] ?? "body";
  const base = COLORS[mat];
  const isTusk = mat === "tusk";
  for (const [face, def] of Object.entries(el.faces)) {
    const [x1, y1, x2, y2] = def.uv;
    const fw = x2 - x1, fh = y2 - y1;
    const noise = noiseField(8);
    const shade = FACE_SHADE[face] ?? 0;
    for (let yy = 0; yy < fh; yy++) {
      for (let xx = 0; xx < fw; xx++) {
        let r = base[0], g = base[1], b = base[2];
        if (!isTusk) {
          const n = noise(xx / Math.max(1, fw - 1), yy / Math.max(1, fh - 1));
          const w = Math.pow(n, 1.5) * WRINKLE_MIX;
          r = r * (1 - w) + COLORS.wrinkle[0] * w;
          g = g * (1 - w) + COLORS.wrinkle[1] * w;
          b = b * (1 - w) + COLORS.wrinkle[2] * w;
        }
        const jg = isTusk ? 5 : GRAIN;
        r += (rnd() * 2 - 1) * jg + shade;
        g += (rnd() * 2 - 1) * jg + shade;
        b += (rnd() * 2 - 1) * jg + shade;
        set(x1 + xx, y1 + yy, clamp(r), clamp(g), clamp(b));
      }
    }
  }
}

// ---- eyes last, on head's forward (north) face -------------------------
{
  const head = model.elements.find((e) => e.name === "head");
  const [x1, y1, x2, y2] = head.faces.north.uv;
  const fw = x2 - x1, fh = y2 - y1;
  const ew = Math.max(2, Math.round(fw * 0.16));
  const eh = Math.max(2, Math.round(fh * 0.16));
  const ey = y1 + Math.round(fh * 0.30);
  const insets = [Math.round(fw * 0.14), x2 - x1 - Math.round(fw * 0.14) - ew];
  for (const ox of insets) {
    for (let yy = 0; yy < eh; yy++)
      for (let xx = 0; xx < ew; xx++)
        set(x1 + ox + xx, ey + yy, ...COLORS.eye);
  }
}

// ---- fill transparency: iterative neighbor bleed + final fallback -------
function alpha(x, y) { return px[(y * W + x) * 4 + 3]; }
for (let pass = 0; pass < 64; pass++) {
  let changed = 0;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (alpha(x, y) === 255) continue;
      const nb = [[1, 0], [-1, 0], [0, 1], [0, -1]];
      for (const [dx, dy] of nb) {
        const nx = x + dx, ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        if (alpha(nx, ny) === 255) {
          const j = (ny * W + nx) * 4;
          set(x, y, px[j], px[j + 1], px[j + 2]);
          changed++;
          break;
        }
      }
    }
  }
  if (!changed) break;
}
for (let i = 0; i < W * H; i++) {
  if (px[i * 4 + 3] !== 255) { px[i * 4] = 120; px[i * 4 + 1] = 120; px[i * 4 + 2] = 126; px[i * 4 + 3] = 255; }
}

// ---- minimal RGBA PNG encoder -----------------------------------------
const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return (buf) => {
    let c = 0xffffffff;
    for (let i = 0; i < buf.length; i++) c = t[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
})();
function chunk(type, data) {
  const tb = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(CRC(Buffer.concat([tb, data])));
  return Buffer.concat([len, tb, data, crc]);
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const raw = Buffer.alloc(H * (1 + W * 4));
for (let y = 0; y < H; y++) {
  raw[y * (1 + W * 4)] = 0;
  Buffer.from(px.buffer, y * W * 4, W * 4).copy(raw, y * (1 + W * 4) + 1);
}
const png = Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  chunk("IHDR", ihdr),
  chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
  chunk("IEND", Buffer.alloc(0)),
]);

model.textures[0].source = "data:image/png;base64," + png.toString("base64");
writeFileSync(url, JSON.stringify(model));

// verify opacity
let bad = 0;
for (let i = 0; i < W * H; i++) if (px[i * 4 + 3] !== 255) bad++;
console.log(`painted ${W}x${H}  transparent-pixels ${bad}  png ${png.length}B`);
