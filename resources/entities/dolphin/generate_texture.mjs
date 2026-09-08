// Paints the dolphin atlas from dolphin.bbmodel's per-face UV rects and writes
// the PNG back into textures[0].source. Re-runnable; bump SEED for a variant.
// Run: make dc CMD="node resources/entities/dolphin/generate_texture.mjs"
import { readFileSync, writeFileSync } from "node:fs";
import zlib from "node:zlib";

const SEED = 20260908;
const GRAIN = 5;                       // smooth skin -> tight per-pixel jitter
const DORSAL = [58, 78, 110];          // dark blue-grey back
const FLANK = [120, 140, 170];         // mid grey-blue side
const BELLY = [214, 224, 236];         // pale flat belly
const FIN = [70, 90, 122];             // fins slightly darker than flank
const MOTTLE = [92, 110, 140];         // subtle blotch accent
const EYE = [16, 16, 22];              // near-black glossy eye

const FIN_ELEMENTS = new Set(["dorsalFin", "pectoralFinL", "pectoralFinR", "tailFluke"]);

// ---- seeded PRNG (mulberry32) ----
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
const lerp = (a, b, t) => a + (b - a) * t;
const mix = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];

// coarse value-noise field per element for mottling
function noiseField(n) {
  const g = Array.from({ length: n * n }, () => rnd());
  return (u, v) => {
    const x = u * (n - 1), y = v * (n - 1);
    const x0 = Math.floor(x), y0 = Math.floor(y);
    const x1 = Math.min(x0 + 1, n - 1), y1 = Math.min(y0 + 1, n - 1);
    const fx = x - x0, fy = y - y0;
    const a = lerp(g[y0 * n + x0], g[y0 * n + x1], fx);
    const b = lerp(g[y1 * n + x0], g[y1 * n + x1], fx);
    return lerp(a, b, fy);
  };
}

const modelPath = new URL("./dolphin.bbmodel", import.meta.url);
const model = JSON.parse(readFileSync(modelPath, "utf8"));
const W = model.resolution.width, H = model.resolution.height;
const px = new Uint8Array(W * H * 4); // RGBA, starts fully transparent

function setPx(x, y, [r, g, b]) {
  const i = (y * W + x) * 4;
  px[i] = clamp(r); px[i + 1] = clamp(g); px[i + 2] = clamp(b); px[i + 3] = 255;
}

// base color for a face: vertical position on body drives dorsal/belly blend
function faceBase(elemName, face) {
  if (FIN_ELEMENTS.has(elemName)) return { top: FIN, bot: FIN };
  switch (face) {
    case "up": return { top: DORSAL, bot: DORSAL };
    case "down": return { top: BELLY, bot: BELLY };
    default: return { top: mix(DORSAL, FLANK, 0.35), bot: mix(FLANK, BELLY, 0.5) };
  }
}

for (const e of model.elements) {
  for (const [face, def] of Object.entries(e.faces)) {
    const [x1, y1, x2, y2] = def.uv;
    const w = x2 - x1, h = y2 - y1;
    if (w <= 0 || h <= 0) continue;
    const { top, bot } = faceBase(e.name, face);
    const nz = noiseField(8);
    const shade = face === "up" ? 1.06 : face === "down" ? 0.96 : 1.0;
    for (let yy = 0; yy < h; yy++) {
      for (let xx = 0; xx < w; xx++) {
        const u = w > 1 ? xx / (w - 1) : 0;
        const v = h > 1 ? yy / (h - 1) : 0;
        let c = mix(top, bot, v);
        const m = nz(u, v);
        if (m > 0.62) c = mix(c, MOTTLE, (m - 0.62) * 0.8);
        c = c.map((ch) => ch * shade + (rnd() * 2 - 1) * GRAIN);
        setPx(x1 + xx, y1 + yy, c);
      }
    }
  }
}

// ---- eyes last, on the head's forward (north) face ----
const head = model.elements.find((e) => e.name === "head");
{
  const [hx1, hy1, hx2] = head.faces.north.uv;
  const hw = hx2 - hx1, hh = head.faces.north.uv[3] - hy1;
  const ew = Math.max(1, Math.round(hw * 0.16));
  const eh = Math.max(1, Math.round(hh * 0.16));
  const eyeY = hy1 + Math.round(hh * 0.5);
  for (const ex of [hx1 + 1, hx2 - 1 - ew]) {
    for (let yy = 0; yy < eh; yy++)
      for (let xx = 0; xx < ew; xx++) setPx(ex + xx, eyeY + yy, EYE);
  }
}

// ---- fill transparent gutters: iterative neighbour bleed + hard fallback ----
function bleed() {
  const src = px.slice();
  let changed = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y * W + x) * 4;
    if (src[i + 3] === 255) continue;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]) {
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
      const j = (ny * W + nx) * 4;
      if (src[j + 3] === 255) {
        px[i] = src[j]; px[i + 1] = src[j + 1]; px[i + 2] = src[j + 2]; px[i + 3] = 255;
        changed++; break;
      }
    }
  }
  return changed;
}
for (let k = 0; k < W + H && bleed() > 0; k++);
for (let i = 0; i < px.length; i += 4) {
  if (px[i + 3] !== 255) { px[i] = FLANK[0]; px[i + 1] = FLANK[1]; px[i + 2] = FLANK[2]; px[i + 3] = 255; }
}

// ---- minimal RGBA PNG codec (zlib + CRC32) ----
const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return (buf) => {
    let c = 0xffffffff;
    for (const b of buf) c = t[(c ^ b) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
})();
function chunk(type, data) {
  const tb = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(CRC(Buffer.concat([tb, data])));
  return Buffer.concat([len, tb, data, crc]);
}
function encodePng(width, height, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const raw = Buffer.alloc(height * (1 + width * 4));
  for (let y = 0; y < height; y++) {
    raw[y * (1 + width * 4)] = 0;
    rgba.subarray(y * width * 4, (y + 1) * width * 4)
      .forEach((b, k) => { raw[y * (1 + width * 4) + 1 + k] = b; });
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}
function decodePngAlphaOk(buf) {
  let off = 8; const idat = [];
  let w = 0, h = 0;
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString("ascii", off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === "IHDR") { w = data.readUInt32BE(0); h = data.readUInt32BE(4); }
    if (type === "IDAT") idat.push(data);
    if (type === "IEND") break;
    off += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * 4;
  const out = Buffer.alloc(h * stride);
  const paeth = (a, b, c) => {
    const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
    return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
  };
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (stride + 1)];
    const row = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    for (let x = 0; x < stride; x++) {
      const a = x >= 4 ? out[y * stride + x - 4] : 0;
      const b = y > 0 ? out[(y - 1) * stride + x] : 0;
      const c = x >= 4 && y > 0 ? out[(y - 1) * stride + x - 4] : 0;
      let val = row[x];
      if (ft === 1) val += a; else if (ft === 2) val += b;
      else if (ft === 3) val += (a + b) >> 1; else if (ft === 4) val += paeth(a, b, c);
      out[y * stride + x] = val & 0xff;
    }
  }
  for (let i = 3; i < out.length; i += 4) if (out[i] !== 255) return false;
  return true;
}

const png = encodePng(W, H, px);
if (!decodePngAlphaOk(png)) throw new Error("texture has transparent pixels");
model.textures[0].source = "data:image/png;base64," + png.toString("base64");
writeFileSync(modelPath, JSON.stringify(model));
writeFileSync(new URL("./dolphin_preview.png", import.meta.url), png);
console.log(`painted ${W}x${H} atlas, fully opaque, eyes on head north face`);
