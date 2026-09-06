// Paints resources/entities/snake_man/snake_man.bbmodel's embedded texture atlas.
// Re-runnable: reads per-face UV rects from the sibling bbmodel, paints procedural
// snakeskin (dark green scales + pale belly + diamond macro pattern) + slit venom-yellow
// eyes, writes the PNG back into textures[0].source.
// Run: make dc CMD="node resources/entities/snake_man/generate_texture.mjs"
import { readFileSync, writeFileSync } from "node:fs";
import zlib from "node:zlib";

// ---- tunables -------------------------------------------------------------
const SEED = 0x5171a3;
const COLORS = {
  scale: [46, 92, 54],   // dark green
  hood: [38, 78, 46],    // slightly darker hood
  belly: [176, 186, 128], // pale yellow-green underside / tail
  scaleAccent: [30, 64, 40], // dark diamond outline
  bellyAccent: [150, 160, 104],
  eye: [214, 196, 44],   // venom yellow
  pupil: [20, 24, 12],   // vertical slit
};
const GRAIN = 8;
const SCALE_PX = 3;        // diamond/scale cell size in px
const PATTERN_MIX = 0.55;
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

const url = new URL("./snake_man.bbmodel", import.meta.url);
const model = JSON.parse(readFileSync(url, "utf8"));
const W = model.resolution.width;
const H = model.resolution.height;
const px = new Uint8Array(W * H * 4);
function set(x, y, r, g, b) {
  const i = (y * W + x) * 4;
  px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = 255;
}

const MAT_BY_ELEMENT = {
  body: "scale", head: "scale", snout: "scale", hood: "hood",
  rightArm: "scale", leftArm: "scale", rightLeg: "scale", leftLeg: "scale",
  tailA: "belly", tailB: "belly",
};
const FACE_SHADE = { up: 10, down: -12, north: 3, south: -5, east: -2, west: -2 };

// diamond scale mask: 1 on the cell body, 0 near the cell edges (outline)
function scaleMask(x, y) {
  const u = ((x % SCALE_PX) + SCALE_PX) % SCALE_PX;
  const v = ((y % SCALE_PX) + SCALE_PX) % SCALE_PX;
  const cx = SCALE_PX / 2, cy = SCALE_PX / 2;
  const d = Math.abs(u - cx) / cx + Math.abs(v - cy) / cy; // diamond distance 0..2
  return d < 1.15 ? 1 : 0;
}

for (const el of model.elements) {
  const mat = MAT_BY_ELEMENT[el.name] ?? "scale";
  const isBelly = mat === "belly";
  const base = COLORS[mat];
  const accent = isBelly ? COLORS.bellyAccent : COLORS.scaleAccent;
  for (const [face, def] of Object.entries(el.faces)) {
    const [x1, y1, x2, y2] = def.uv;
    const fw = x2 - x1, fh = y2 - y1;
    const shade = FACE_SHADE[face] ?? 0;
    const ox = (rnd() * SCALE_PX) | 0, oy = (rnd() * SCALE_PX) | 0;
    for (let yy = 0; yy < fh; yy++) {
      for (let xx = 0; xx < fw; xx++) {
        let r = base[0], g = base[1], b = base[2];
        const m = scaleMask(xx + ox, yy + oy);
        const w = (m ? 0 : 1) * PATTERN_MIX; // blend accent into the scale outline
        r = r * (1 - w) + accent[0] * w;
        g = g * (1 - w) + accent[1] * w;
        b = b * (1 - w) + accent[2] * w;
        r += (rnd() * 2 - 1) * GRAIN + shade;
        g += (rnd() * 2 - 1) * GRAIN + shade;
        b += (rnd() * 2 - 1) * GRAIN + shade;
        set(x1 + xx, y1 + yy, clamp(r), clamp(g), clamp(b));
      }
    }
  }
}

// ---- eyes last: slit pupils on the head's forward (north) face ---------
{
  const head = model.elements.find((e) => e.name === "head");
  const [x1, y1, x2, y2] = head.faces.north.uv;
  const fw = x2 - x1, fh = y2 - y1;
  const ew = Math.max(2, Math.round(fw * 0.20));
  const eh = Math.max(2, Math.round(fh * 0.22));
  const ey = y1 + Math.round(fh * 0.28);
  const insets = [Math.round(fw * 0.10), x2 - x1 - Math.round(fw * 0.10) - ew];
  for (const oxp of insets) {
    for (let yy = 0; yy < eh; yy++) {
      for (let xx = 0; xx < ew; xx++) {
        const mid = Math.floor(ew / 2);
        const isSlit = xx === mid || xx === mid - 1;
        set(x1 + oxp + xx, ey + yy, ...(isSlit ? COLORS.pupil : COLORS.eye));
      }
    }
  }
}

// ---- fill transparency ------------------------------------------------
function alpha(x, y) { return px[(y * W + x) * 4 + 3]; }
for (let pass = 0; pass < 64; pass++) {
  let changed = 0;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (alpha(x, y) === 255) continue;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
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
  if (px[i * 4 + 3] !== 255) { px[i * 4] = 46; px[i * 4 + 1] = 92; px[i * 4 + 2] = 54; px[i * 4 + 3] = 255; }
}

// ---- minimal RGBA PNG encoder ---------------------------------------
const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  return (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = t[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
})();
function chunk(type, data) {
  const tb = Buffer.from(type, "ascii");
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(CRC(Buffer.concat([tb, data])));
  return Buffer.concat([len, tb, data, crc]);
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8; ihdr[9] = 6;
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

let bad = 0;
for (let i = 0; i < W * H; i++) if (px[i * 4 + 3] !== 255) bad++;
console.log(`painted ${W}x${H}  transparent-pixels ${bad}  png ${png.length}B`);
