// Procedural texture generator for camel.bbmodel.
// Re-runnable: reads sibling camel.bbmodel UV rects, paints them, writes PNG back
// into textures[0].source. Bump SEED for a same-style variant.
// Run: make dc CMD="node resources/entities/camel/generate_texture.mjs"
import { readFileSync, writeFileSync } from "node:fs";
import zlib from "node:zlib";

const SEED = 0xca3e1;
const GRAIN = { fur: 11, smooth: 6 };
const PALETTE = {
  base:   [217, 164, 65],   // sand
  accent: [150, 112, 50],   // darker mottle
  belly:  [181, 134, 58],
  muzzle: [232, 197, 107],
  ear:    [198, 150, 60],
  eye:    [40, 34, 30],
};

// ---------- PRNG ----------
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ---------- PNG codec ----------
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
function encodePNG(w, h, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) {
    raw[y * (1 + w * 4)] = 0;
    rgba.copy(raw, y * (1 + w * 4) + 1, y * w * 4, (y + 1) * w * 4);
  }
  return Buffer.concat([
    sig, chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0)),
  ]);
}
function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}
function decodePNG(buf) {
  let p = 8, w = 0, h = 0;
  const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p);
    const type = buf.toString("ascii", p + 4, p + 8);
    const data = buf.subarray(p + 8, p + 8 + len);
    if (type === "IHDR") { w = data.readUInt32BE(0); h = data.readUInt32BE(4); }
    else if (type === "IDAT") idat.push(data);
    else if (type === "IEND") break;
    p += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * 4;
  const out = Buffer.alloc(h * stride);
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (stride + 1)];
    const row = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    for (let x = 0; x < stride; x++) {
      const a = x >= 4 ? out[y * stride + x - 4] : 0;
      const b = y > 0 ? out[(y - 1) * stride + x] : 0;
      const c = x >= 4 && y > 0 ? out[(y - 1) * stride + x - 4] : 0;
      let v = row[x];
      if (ft === 1) v += a;
      else if (ft === 2) v += b;
      else if (ft === 3) v += (a + b) >> 1;
      else if (ft === 4) v += paeth(a, b, c);
      out[y * stride + x] = v & 0xff;
    }
  }
  return { w, h, data: out };
}

// ---------- load model ----------
const url = new URL("./camel.bbmodel", import.meta.url);
const model = JSON.parse(readFileSync(url, "utf8"));
const W = model.resolution.width, H = model.resolution.height;
const canvas = Buffer.alloc(W * H * 4); // all-zero => transparent
const rnd = mulberry32(SEED);

function clamp8(v) { return v < 0 ? 0 : v > 255 ? 255 : v | 0; }
function setPx(x, y, r, g, b) {
  const i = (y * W + x) * 4;
  canvas[i] = r; canvas[i + 1] = g; canvas[i + 2] = b; canvas[i + 3] = 255;
}

function materialFor(name) {
  if (name === "muzzle") return { base: PALETTE.muzzle, accent: PALETTE.base, grain: GRAIN.smooth };
  if (name === "earR" || name === "earL") return { base: PALETTE.ear, accent: PALETTE.accent, grain: GRAIN.fur };
  return { base: PALETTE.base, accent: PALETTE.accent, grain: GRAIN.fur };
}
function aoFor(face, name) {
  if (face === "up") return 10;
  if (face === "down") return name === "body" ? -26 : -16;
  if (face === "north" || face === "south") return -4;
  return 0;
}

// 8x8 value-noise field per element, bilinear sampled
function makeNoise() {
  const g = [];
  for (let i = 0; i < 8 * 8; i++) g.push(rnd());
  return (u, v) => {
    const fx = u * 7, fy = v * 7;
    const x0 = Math.min(6, Math.floor(fx)), y0 = Math.min(6, Math.floor(fy));
    const tx = fx - x0, ty = fy - y0;
    const a = g[y0 * 8 + x0], b = g[y0 * 8 + x0 + 1];
    const c = g[(y0 + 1) * 8 + x0], d = g[(y0 + 1) * 8 + x0 + 1];
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
  };
}

for (const el of model.elements) {
  const mat = materialFor(el.name);
  for (const [face, f] of Object.entries(el.faces)) {
    const [x1, y1, x2, y2] = f.uv;
    const fw = x2 - x1, fh = y2 - y1;
    const noise = makeNoise();
    const ao = aoFor(face, el.name);
    // belly tint on body/neck lower faces
    const belly = el.name === "body" && face === "down";
    for (let py = 0; py < fh; py++) {
      for (let px = 0; px < fw; px++) {
        let [r, g, b] = belly ? PALETTE.belly : mat.base;
        const n = noise((px + 0.5) / fw, (py + 0.5) / fh);
        if (n > 0.62) {
          const t = Math.min(1, (n - 0.62) / 0.38) * 0.55;
          r = r + (mat.accent[0] - r) * t;
          g = g + (mat.accent[1] - g) * t;
          b = b + (mat.accent[2] - b) * t;
        }
        const j = (rnd() * 2 - 1) * mat.grain;
        setPx(x1 + px, y1 + py,
          clamp8(r + j + ao), clamp8(g + j * 0.9 + ao), clamp8(b + j * 0.8 + ao));
      }
    }
  }
}

// ---------- eyes: head north face, painted last ----------
const head = model.elements.find((e) => e.name === "head");
{
  const [x1, y1, x2] = head.faces.north.uv;
  const fw = x2 - x1;
  const fh = head.faces.north.uv[3] - y1;
  const es = Math.max(1, Math.round(fw * 0.16));
  const inset = Math.max(1, Math.round(fw * 0.14));
  const ey = y1 + Math.round(fh * 0.32);
  const [er, eg, eb] = PALETTE.eye;
  for (const ex of [x1 + inset, x2 - inset - es]) {
    for (let dy = 0; dy < es; dy++)
      for (let dx = 0; dx < es; dx++) setPx(ex + dx, ey + dy, er, eg, eb);
  }
}

// ---------- opaque fill: neighbour bleed then hard fallback ----------
function bleedPass() {
  let changed = 0;
  const copy = Buffer.from(canvas);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const i = (y * W + x) * 4;
      if (copy[i + 3] === 255) continue;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]) {
        const nx = x + dx, ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        const j = (ny * W + nx) * 4;
        if (copy[j + 3] === 255) {
          canvas[i] = copy[j]; canvas[i + 1] = copy[j + 1];
          canvas[i + 2] = copy[j + 2]; canvas[i + 3] = 255;
          changed++;
          break;
        }
      }
    }
  }
  return changed;
}
for (let k = 0; k < 64; k++) if (bleedPass() === 0) break;
for (let i = 0; i < W * H; i++) {
  if (canvas[i * 4 + 3] !== 255) {
    canvas[i * 4] = PALETTE.base[0]; canvas[i * 4 + 1] = PALETTE.base[1];
    canvas[i * 4 + 2] = PALETTE.base[2]; canvas[i * 4 + 3] = 255;
  }
}

// ---------- encode + verify ----------
const png = encodePNG(W, H, canvas);
const dec = decodePNG(png);
let opaque = true;
for (let i = 0; i < dec.w * dec.h; i++) if (dec.data[i * 4 + 3] !== 255) { opaque = false; break; }
if (!opaque) throw new Error("texture has transparent pixels after fill");

model.textures[0].source = "data:image/png;base64," + png.toString("base64");
model.textures[0].width = W;
model.textures[0].height = H;
model.textures[0].uv_width = W;
model.textures[0].uv_height = H;
writeFileSync(url, JSON.stringify(model));
console.log(`texture ${W}x${H} written, all pixels opaque, eyes painted on head north`);
