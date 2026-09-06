// Procedural texture generator for the mountain_goat NPC.
// Reads UV rects from the sibling bbmodel, paints fur/horn/hoof pixels, writes
// the result back into textures[0].source. Re-run after tweaking the palette
// or SEED below to regenerate without touching geometry.
import fs from "node:fs";
import zlib from "node:zlib";

const MODEL_PATH = new URL("./mountain_goat.bbmodel", import.meta.url);
const SEED = 20260906;

// ---- palette --------------------------------------------------------------
const WHITE = [235, 232, 224];
const BEIGE = [188, 160, 122];
const BROWN = [110, 82, 55];
const HORN_GRAY = [70, 68, 66];
const HORN_GRAY_DARK = [40, 39, 38];
const HOOF_BLACK = [25, 24, 23];
const EYE_COLOR = [20, 15, 10];

// per-element skin material
const SKIN = {
  body: "wool",
  head: "wool",
  snout: "wool_dark",
  earL: "wool",
  earR: "wool",
  hornL_base: "horn",
  hornL_tip: "horn",
  hornR_base: "horn",
  hornR_tip: "horn",
  tail: "wool",
  frontLegL: "leg",
  frontLegR: "leg",
  backLegL: "leg",
  backLegR: "leg",
};

// ---- seeded PRNG (mulberry32) ---------------------------------------------
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(SEED);

function clamp255(v) {
  return Math.max(0, Math.min(255, v));
}
function jitter(color, amount) {
  return color.map((c) => clamp255(c + Math.round((rand() * 2 - 1) * amount)));
}

// coarse value-noise grid, bilinearly sampled, for blotchy wool patches
function makeNoiseGrid(gw, gh) {
  const g = [];
  for (let y = 0; y <= gh; y++) {
    const row = [];
    for (let x = 0; x <= gw; x++) row.push(rand());
    g.push(row);
  }
  return g;
}
function sampleNoise(grid, gw, gh, u, v) {
  const fx = u * gw;
  const fy = v * gh;
  const x0 = Math.floor(fx);
  const y0 = Math.floor(fy);
  const x1 = Math.min(x0 + 1, gw);
  const y1 = Math.min(y0 + 1, gh);
  const tx = fx - x0;
  const ty = fy - y0;
  const a = grid[y0][x0];
  const b = grid[y0][x1];
  const c = grid[y1][x0];
  const d = grid[y1][x1];
  return a * (1 - tx) * (1 - ty) + b * tx * (1 - ty) + c * (1 - tx) * ty + d * tx * ty;
}

// ---- minimal PNG codec (node zlib, no deps) --------------------------------
const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const typeBuf = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}
function encodePng(width, height, rgba) {
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter: None
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const idatData = zlib.deflateSync(raw);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idatData), chunk("IEND", Buffer.alloc(0))]);
}
function decodePng(buf) {
  let offset = 8;
  let width = 0,
    height = 0;
  const idatParts = [];
  while (offset < buf.length) {
    const len = buf.readUInt32BE(offset);
    const type = buf.toString("ascii", offset + 4, offset + 8);
    const data = buf.subarray(offset + 8, offset + 8 + len);
    if (type === "IHDR") {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
    } else if (type === "IDAT") {
      idatParts.push(data);
    }
    offset += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idatParts));
  const stride = width * 4;
  const rgba = Buffer.alloc(stride * height);
  let prevRow = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const rowStart = y * (stride + 1);
    const filter = raw[rowStart];
    const row = Buffer.from(raw.subarray(rowStart + 1, rowStart + 1 + stride));
    for (let x = 0; x < stride; x++) {
      const bpp = 4;
      const a = x >= bpp ? row[x - bpp] : 0;
      const b = prevRow[x];
      const c = x >= bpp ? prevRow[x - bpp] : 0;
      let pred = 0;
      if (filter === 1) pred = a;
      else if (filter === 2) pred = b;
      else if (filter === 3) pred = Math.floor((a + b) / 2);
      else if (filter === 4) {
        const p = a + b - c;
        const pa = Math.abs(p - a),
          pb = Math.abs(p - b),
          pc = Math.abs(p - c);
        pred = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      row[x] = (row[x] + pred) & 0xff;
    }
    row.copy(rgba, y * stride);
    prevRow = row;
  }
  return { width, height, rgba };
}

// ---- main -------------------------------------------------------------
const model = JSON.parse(fs.readFileSync(MODEL_PATH));
const { width, height } = model.resolution;
const canvas = Buffer.alloc(width * height * 4, 0); // all transparent initially
const painted = new Uint8Array(width * height); // track which pixels were explicitly set

function setPixel(x, y, color) {
  const idx = (y * width + x) * 4;
  canvas[idx] = color[0];
  canvas[idx + 1] = color[1];
  canvas[idx + 2] = color[2];
  canvas[idx + 3] = 255;
  painted[y * width + x] = 1;
}

const woolNoise = makeNoiseGrid(10, 10);

function paintFace(x1, y1, x2, y2, skin, shadeDelta) {
  const w = x2 - x1;
  const h = y2 - y1;
  for (let y = y1; y < y2; y++) {
    for (let x = x1; x < x2; x++) {
      const u = (x - x1) / Math.max(1, w);
      const v = (y - y1) / Math.max(1, h);
      let base;
      if (skin === "wool" || skin === "wool_dark") {
        const n = sampleNoise(woolNoise, 10, 10, (x / width + u) * 0.5, (y / height + v) * 0.5);
        const blend = skin === "wool_dark" ? 0.5 : n > 0.62 ? 0.75 : n > 0.45 ? 0.35 : 0;
        base = [
          WHITE[0] + (blend > 0.5 ? BROWN[0] - WHITE[0] : BEIGE[0] - WHITE[0]) * blend,
          WHITE[1] + (blend > 0.5 ? BROWN[1] - WHITE[1] : BEIGE[1] - WHITE[1]) * blend,
          WHITE[2] + (blend > 0.5 ? BROWN[2] - WHITE[2] : BEIGE[2] - WHITE[2]) * blend,
        ];
        base = jitter(base, 10);
      } else if (skin === "horn") {
        const n = sampleNoise(woolNoise, 10, 10, x / width, y / height);
        // horizontal ring striping
        const ring = Math.sin(y * 1.8) * 0.5 + 0.5;
        const dark = ring > 0.5 || n > 0.7;
        base = jitter(dark ? HORN_GRAY_DARK : HORN_GRAY, 6);
      } else if (skin === "leg") {
        // brown lower leg fading to white, black hoof at the very bottom
        const isHoof = v > 0.82;
        if (isHoof) {
          base = jitter(HOOF_BLACK, 4);
        } else if (v > 0.45) {
          base = jitter(BROWN, 8);
        } else {
          base = jitter(WHITE, 8);
        }
      } else {
        base = jitter(BEIGE, 8);
      }
      const shaded = shadeDelta ? base.map((c) => clamp255(c + shadeDelta)) : base;
      setPixel(x, y, shaded);
    }
  }
}

const SHADE = { up: 8, down: -10, north: 0, south: -4, east: -2, west: -2 };

for (const el of model.elements) {
  const skin = SKIN[el.name] ?? "wool";
  for (const [face, { uv }] of Object.entries(el.faces)) {
    const [x1, y1, x2, y2] = uv;
    paintFace(x1, y1, x2, y2, skin, SHADE[face] ?? 0);
  }
}

// ---- eyes: painted last, on the head element's forward-facing (north) face ----
// Blockbench convention: north = -z normal. The head box extends toward -z
// (snout side), so north is the face that reads as "front" once the head
// bone's -45deg forward pitch tilts it down toward the ground.
const headEl = model.elements.find((e) => e.name === "head");
{
  const [x1, y1, x2, y2] = headEl.faces.north.uv;
  const w = x2 - x1;
  const h = y2 - y1;
  const eyeW = Math.max(1, Math.round(w * 0.18));
  const eyeH = Math.max(1, Math.round(h * 0.18));
  const inset = Math.max(1, Math.round(w * 0.12));
  const eyeY = y1 + Math.round(h * 0.35);
  for (let dy = 0; dy < eyeH; dy++) {
    for (let dx = 0; dx < eyeW; dx++) {
      setPixel(x1 + inset + dx, eyeY + dy, EYE_COLOR);
      setPixel(x2 - inset - eyeW + dx, eyeY + dy, EYE_COLOR);
    }
  }
}

// ---- neighbor-bleed pass to kill transparent gutters, then opaque fallback --
function idx(x, y) {
  return y * width + x;
}
for (let iter = 0; iter < Math.max(width, height); iter++) {
  let remaining = 0;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (painted[idx(x, y)]) continue;
      remaining++;
      const neighbors = [
        [x - 1, y],
        [x + 1, y],
        [x, y - 1],
        [x, y + 1],
      ];
      for (const [nx, ny] of neighbors) {
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
        if (!painted[idx(nx, ny)]) continue;
        const nIdx = idx(nx, ny) * 4;
        setPixel(x, y, [canvas[nIdx], canvas[nIdx + 1], canvas[nIdx + 2]]);
        break;
      }
    }
  }
  if (remaining === 0) break;
}
// fallback fill for anything the bleed still didn't reach (fully enclosed blanks)
for (let y = 0; y < height; y++) {
  for (let x = 0; x < width; x++) {
    if (!painted[idx(x, y)]) setPixel(x, y, BEIGE);
  }
}

// verify full opacity
for (let i = 3; i < canvas.length; i += 4) {
  if (canvas[i] !== 255) throw new Error(`transparent pixel remains at byte ${i}`);
}

const png = encodePng(width, height, canvas);
model.textures[0].source = `data:image/png;base64,${png.toString("base64")}`;
fs.writeFileSync(MODEL_PATH, JSON.stringify(model, null, 1));
console.log("painted texture", width, "x", height, "into", MODEL_PATH.pathname);

// sanity: decode back and confirm opacity
const roundTrip = decodePng(png);
for (let i = 3; i < roundTrip.rgba.length; i += 4) {
  if (roundTrip.rgba[i] !== 255) throw new Error("round-trip decode found transparent pixel");
}
console.log("round-trip decode OK, fully opaque");
