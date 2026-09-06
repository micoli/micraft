// Procedural texture generator for polar_bear.bbmodel.
// Re-run after editing constants below to regenerate colors/pattern without touching geometry.
import fs from "node:fs";
import zlib from "node:zlib";

const SEED = 20260906;
const BASE_DIR = new URL(".", import.meta.url);
const BBMODEL_PATH = new URL("./polar_bear.bbmodel", BASE_DIR);
const SKIN_MAP_PATH = new URL("./_skin_map.json", BASE_DIR);

const COLORS = {
  fur_white: { r: 232, g: 232, b: 236 },
  fur_cream: { r: 224, g: 213, b: 184 },
  claw: { r: 15, g: 15, b: 15 },
  eye: { r: 12, g: 12, b: 12 },
  foot_shade: { r: 175, g: 172, b: 168 }, // darker foot band
};
const SIDE_SHADE = { north: 0, south: -6, east: -10, west: -10, up: 10, down: -18 };
const GRAIN = 9; // per-channel jitter amplitude for fur

function mulberry32(seed) {
  let a = seed;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(SEED);

// ---- minimal PNG encode (8-bit RGBA, filter None, zlib deflate) ----
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
  const typeBuf = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}
function encodePNG(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    const rowStart = y * (width * 4 + 1);
    raw[rowStart] = 0; // filter type None
    rgba.copy(raw, rowStart + 1, y * width * 4, (y + 1) * width * 4);
  }
  const idat = zlib.deflateSync(raw);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}
function decodePNG(buf) {
  let off = 8;
  let width = 0,
    height = 0;
  const idatChunks = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString("ascii", off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === "IHDR") {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
    } else if (type === "IDAT") {
      idatChunks.push(data);
    }
    off += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idatChunks));
  const rgba = Buffer.alloc(width * height * 4);
  const stride = width * 4;
  let prevRow = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const rowStart = y * (stride + 1);
    const filter = raw[rowStart];
    const row = Buffer.from(raw.subarray(rowStart + 1, rowStart + 1 + stride));
    for (let x = 0; x < stride; x++) {
      const a = x >= 4 ? row[x - 4] : 0;
      const b = prevRow[x];
      const c = x >= 4 ? prevRow[x - 4] : 0;
      let val = row[x];
      if (filter === 1) val = (val + a) & 0xff;
      else if (filter === 2) val = (val + b) & 0xff;
      else if (filter === 3) val = (val + Math.floor((a + b) / 2)) & 0xff;
      else if (filter === 4) {
        const p = a + b - c;
        const pa = Math.abs(p - a),
          pb = Math.abs(p - b),
          pc = Math.abs(p - c);
        const pr = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
        val = (val + pr) & 0xff;
      }
      row[x] = val;
    }
    row.copy(rgba, y * stride);
    prevRow = row;
  }
  return { width, height, rgba };
}

// ---- build ----
const bbmodel = JSON.parse(fs.readFileSync(BBMODEL_PATH, "utf8"));
const skinMap = JSON.parse(fs.readFileSync(SKIN_MAP_PATH, "utf8"));
const W = bbmodel.resolution.width;
const H = bbmodel.resolution.height;
const rgba = Buffer.alloc(W * H * 4, 0); // starts fully transparent

function setPixel(x, y, r, g, b) {
  const i = (y * W + x) * 4;
  rgba[i] = r;
  rgba[i + 1] = g;
  rgba[i + 2] = b;
  rgba[i + 3] = 255;
}

function paintFurRect(uv, baseColor, faceName) {
  const [x1, y1, x2, y2] = uv;
  const shade = SIDE_SHADE[faceName] ?? 0;
  for (let y = y1; y < y2; y++) {
    for (let x = x1; x < x2; x++) {
      const j = () => Math.floor((rand() - 0.5) * 2 * GRAIN);
      const r = clamp(baseColor.r + shade + j());
      const g = clamp(baseColor.g + shade + j());
      const b = clamp(baseColor.b + shade + j());
      setPixel(x, y, r, g, b);
    }
  }
}
function clamp(v) {
  return Math.max(0, Math.min(255, v));
}
function fillRect(uv, color) {
  const [x1, y1, x2, y2] = uv;
  for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) setPixel(x, y, color.r, color.g, color.b);
}

for (const el of bbmodel.elements) {
  const skinInfo = skinMap[el.name];
  const color = COLORS[skinInfo.skin];
  for (const [faceName, face] of Object.entries(el.faces)) {
    paintFurRect(face.uv, color, faceName);
  }
  if (skinInfo.part === "leg") {
    // darker foot band: bottom quarter of side faces
    for (const faceName of ["north", "south", "east", "west"]) {
      const [x1, y1, x2, y2] = el.faces[faceName].uv;
      const bandHeight = Math.max(1, Math.round((y2 - y1) * 0.25));
      paintFurRect([x1, y2 - bandHeight, x2, y2], COLORS.foot_shade, faceName);
    }
    // claws on down face: thin dark strip along the front edge
    const [dx1, dy1, dx2] = el.faces.down.uv;
    const clawHeight = Math.max(1, Math.round((el.faces.down.uv[3] - dy1) * 0.35));
    fillRect([dx1, dy1, dx2, dy1 + clawHeight], COLORS.claw);
  }
}

// eyes + nostrils painted last on the snout's north (forward) face, overwriting fur pixels there
const snout = bbmodel.elements.find((e) => e.name === "snout");
{
  const [x1, y1, x2, y2] = snout.faces.north.uv;
  const w = x2 - x1;
  const h = y2 - y1;
  const eyeW = Math.max(1, Math.round(w * 0.18));
  const eyeH = Math.max(1, Math.round(h * 0.18));
  const eyeY = y1 + Math.round(h * 0.15);
  fillRect([x1 + 1, eyeY, x1 + 1 + eyeW, eyeY + eyeH], COLORS.eye);
  fillRect([x2 - 1 - eyeW, eyeY, x2 - 1, eyeY + eyeH], COLORS.eye);
  const noseW = Math.max(1, Math.round(w * 0.3));
  const noseH = Math.max(1, Math.round(h * 0.25));
  const noseX = x1 + Math.round((w - noseW) / 2);
  const noseY = y2 - noseH - 1;
  fillRect([noseX, noseY, noseX + noseW, noseY + noseH], COLORS.eye);
}

// neighbor-bleed pass to opacify any leftover atlas gutters
function bleedPass() {
  let changed = false;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const i = (y * W + x) * 4;
      if (rgba[i + 3] === 255) continue;
      for (const [dx, dy] of [
        [1, 0],
        [-1, 0],
        [0, 1],
        [0, -1],
      ]) {
        const nx = x + dx,
          ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        const ni = (ny * W + nx) * 4;
        if (rgba[ni + 3] === 255) {
          rgba[i] = rgba[ni];
          rgba[i + 1] = rgba[ni + 1];
          rgba[i + 2] = rgba[ni + 2];
          rgba[i + 3] = 255;
          changed = true;
          break;
        }
      }
    }
  }
  return changed;
}
for (let iter = 0; iter < Math.max(W, H); iter++) {
  if (!bleedPass()) break;
}
// fallback: force-fill anything still transparent
for (let i = 0; i < rgba.length; i += 4) {
  if (rgba[i + 3] !== 255) {
    rgba[i] = COLORS.fur_white.r;
    rgba[i + 1] = COLORS.fur_white.g;
    rgba[i + 2] = COLORS.fur_white.b;
    rgba[i + 3] = 255;
  }
}

// verify full opacity
for (let i = 3; i < rgba.length; i += 4) {
  if (rgba[i] !== 255) throw new Error(`transparent pixel remains at byte ${i}`);
}

const png = encodePNG(W, H, rgba);
bbmodel.textures[0].source = `data:image/png;base64,${png.toString("base64")}`;
fs.writeFileSync(BBMODEL_PATH, JSON.stringify(bbmodel));

// self-check: decode back and confirm opacity
const decoded = decodePNG(png);
for (let i = 3; i < decoded.rgba.length; i += 4) {
  if (decoded.rgba[i] !== 255) throw new Error("decoded PNG has transparent pixel");
}
console.log(`generated ${W}x${H} texture, ${bbmodel.elements.length} elements painted, opacity verified`);
