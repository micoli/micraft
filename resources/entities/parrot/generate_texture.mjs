// Paints parrot.bbmodel's texture atlas from its packed per-face UV rects.
// Scarlet-macaw palette: red body, yellow+blue wing/tail bands, white cheek, grey beak.
// Run: make dc CMD="node resources/entities/parrot/generate_texture.mjs"

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import zlib from "node:zlib";

const HERE = dirname(fileURLToPath(import.meta.url));
const MODEL_PATH = join(HERE, "parrot.bbmodel");

// ---- tunables -------------------------------------------------------------
const SEED = 0x9e3b21c4;
const GRAIN_FEATHER = 12; // per-channel jitter for feathers
const GRAIN_SMOOTH = 5; // beak / legs

const RED = [196, 58, 54];
const RED_BELLY = [150, 40, 40];
const RED_HEAD = [212, 92, 92];
const YELLOW = [236, 214, 92];
const BLUE = [58, 72, 176];
const CHEEK = [222, 222, 214];
const BEAK_UP = [178, 180, 184];
const BEAK_LOW = [38, 38, 44];
const COLLAR = [24, 24, 28];
const LEG = [52, 52, 58];
const EYE = [244, 224, 128];
const PUPIL = [20, 18, 16];
// ------------------------------------------------------------------------

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
const clamp8 = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);
const grain = (c, amt) => c.map((ch) => clamp8(ch + (rnd() * 2 - 1) * amt));
const shade = (c, f) => c.map((ch) => clamp8(ch * f));

const model = JSON.parse(readFileSync(MODEL_PATH, "utf8"));
const W = model.resolution.width;
const H = model.resolution.height;
const buf = new Uint8Array(W * H * 4); // RGBA, starts fully transparent

const put = (x, y, rgb) => {
  const i = (y * W + x) * 4;
  buf[i] = rgb[0];
  buf[i + 1] = rgb[1];
  buf[i + 2] = rgb[2];
  buf[i + 3] = 255;
};

// wing colour band as a function of distance-from-body fraction t in [0,1]
const wingBand = (t) => (t < 0.42 ? RED : t < 0.68 ? YELLOW : BLUE);

function bigFaceT(elName, faceName, lx, ly, w, h) {
  // returns fraction used for colour banding on the large flat faces, or null
  if (elName === "wing0" || elName === "wing1") {
    if (faceName === "up" || faceName === "down" || faceName === "north" || faceName === "south") {
      const fx = w > 1 ? lx / (w - 1) : 0;
      return elName === "wing1" ? fx : 1 - fx; // body side -> 0
    }
    if (faceName === "east" || faceName === "west") return 0.9; // wingtip
  }
  if (elName === "tail0") {
    if (faceName === "up" || faceName === "down") return (h > 1 ? ly / (h - 1) : 0) * 0.6;
    if (faceName === "east" || faceName === "west") return (w > 1 ? lx / (w - 1) : 0) * 0.6;
    return 0; // ends stay red
  }
  if (elName === "tail1") {
    if (faceName === "up" || faceName === "down") return 0.45 + (h > 1 ? ly / (h - 1) : 0) * 0.55;
    if (faceName === "east" || faceName === "west") return 0.45 + (w > 1 ? lx / (w - 1) : 0) * 0.55;
    return 0.9;
  }
  return null;
}

function baseColor(elName, faceName) {
  if (elName === "upperBeak") return BEAK_UP;
  if (elName === "lowerBeak") return BEAK_LOW;
  if (elName.startsWith("leg") || elName.startsWith("foot")) return LEG;
  if (elName === "head") return RED_HEAD;
  if (elName === "body") return faceName === "down" ? RED_BELLY : RED;
  return RED;
}

function paintFace(el, faceName, rect) {
  const [x1, y1, x2, y2] = rect;
  const w = x2 - x1;
  const h = y2 - y1;
  const smooth = el.name === "upperBeak" || el.name === "lowerBeak" || el.name.startsWith("leg") || el.name.startsWith("foot");
  const amt = smooth ? GRAIN_SMOOTH : GRAIN_FEATHER;
  const dirShade = faceName === "up" ? 1.08 : faceName === "down" ? 0.86 : 1.0;

  for (let ly = 0; ly < h; ly++) {
    for (let lx = 0; lx < w; lx++) {
      let c;
      const t = bigFaceT(el.name, faceName, lx, ly, w, h);
      if (t !== null) c = wingBand(t);
      else c = baseColor(el.name, faceName);

      // head front: white cheek patch on lower part
      if (el.name === "head" && faceName === "north" && ly >= Math.floor(h * 0.35)) c = CHEEK;
      // neck collar: dark bottom row of head side/back/front faces
      if (el.name === "head" && ly === h - 1 && faceName !== "up") c = COLLAR;

      c = grain(shade(c, dirShade), amt);
      put(x1 + lx, y1 + ly, c);
    }
  }
}

for (const el of model.elements)
  for (const [faceName, face] of Object.entries(el.faces)) paintFace(el, faceName, face.uv);

// ---- eyes: painted last on the head front face ---------------------------
{
  const head = model.elements.find((e) => e.name === "head");
  const [x1, y1, x2, y2] = head.faces.north.uv;
  const w = x2 - x1;
  const h = y2 - y1;
  const ew = Math.max(1, Math.round(w * 0.18));
  const eh = Math.max(1, Math.round(h * 0.22));
  const eyY = y1 + Math.floor(h * 0.42);
  for (const ex of [x1 + 1, x2 - 1 - ew]) {
    for (let dy = 0; dy < eh; dy++)
      for (let dx = 0; dx < ew; dx++) put(ex + dx, eyY + dy, EYE);
    const px = ex + (ew >> 1);
    put(px, eyY + (eh >> 1), PUPIL);
  }
}

// ---- gutter fill: bleed opaque neighbours, then hard fallback -----------
const alphaAt = (x, y) => buf[(y * W + x) * 4 + 3];
for (let pass = 0; pass < 64; pass++) {
  let changed = 0;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (alphaAt(x, y) === 255) continue;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx;
        const ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        if (alphaAt(nx, ny) === 255) {
          const i = (ny * W + nx) * 4;
          put(x, y, [buf[i], buf[i + 1], buf[i + 2]]);
          changed++;
          break;
        }
      }
    }
  }
  if (!changed) break;
}
for (let p = 0; p < W * H; p++) if (buf[p * 4 + 3] !== 255) put(p % W, (p / W) | 0, RED);

// ---- minimal RGBA PNG codec ------------------------------------------------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
const crc32 = (b) => {
  let c = 0xffffffff;
  for (let i = 0; i < b.length; i++) c = CRC_TABLE[(c ^ b[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const tb = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([tb, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([len, body, crc]);
};
function encodePng(rgba, w, h) {
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) {
    raw[y * (1 + w * 4)] = 0;
    Buffer.from(rgba.buffer, y * w * 4, w * 4).copy(raw, y * (1 + w * 4) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
function decodePngAlphaOk(png) {
  let o = 8;
  const idat = [];
  let w = 0;
  let h = 0;
  while (o < png.length) {
    const len = png.readUInt32BE(o);
    const type = png.toString("ascii", o + 4, o + 8);
    const data = png.subarray(o + 8, o + 8 + len);
    if (type === "IHDR") {
      w = data.readUInt32BE(0);
      h = data.readUInt32BE(4);
    } else if (type === "IDAT") idat.push(data);
    else if (type === "IEND") break;
    o += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * 4;
  const out = Buffer.alloc(h * stride);
  const paeth = (a, b, c) => {
    const p = a + b - c;
    const pa = Math.abs(p - a);
    const pb = Math.abs(p - b);
    const pc = Math.abs(p - c);
    return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
  };
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (stride + 1)];
    const row = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= 4 ? out[y * stride + i - 4] : 0;
      const b = y > 0 ? out[(y - 1) * stride + i] : 0;
      const c = y > 0 && i >= 4 ? out[(y - 1) * stride + i - 4] : 0;
      let v = row[i];
      if (ft === 1) v += a;
      else if (ft === 2) v += b;
      else if (ft === 3) v += (a + b) >> 1;
      else if (ft === 4) v += paeth(a, b, c);
      out[y * stride + i] = v & 0xff;
    }
  }
  for (let i = 3; i < out.length; i += 4) if (out[i] !== 255) return false;
  return true;
}

const png = encodePng(buf, W, H);
if (!decodePngAlphaOk(png)) throw new Error("texture has transparent pixels after gutter fill");

model.textures[0].source = "data:image/png;base64," + png.toString("base64");
writeFileSync(MODEL_PATH, JSON.stringify(model, null, 2));
console.log(`texture painted: ${W}x${H}, ${png.length} bytes, all pixels opaque`);
