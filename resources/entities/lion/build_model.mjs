// Regenerates lion.bbmodel geometry + UV atlas packing (texture pixels: generate_texture.mjs).
// Run: make dc CMD="node resources/entities/lion/build_model.mjs"
import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const DIR = dirname(fileURLToPath(import.meta.url));
const ATLAS_W = 128;

// part = texture material group (see generate_texture.mjs)
const E = (name, part, from, to, bone) => ({ name, part, from, to, bone });
const elements = [
  E("body", "body", [-5, 10, -9], [5, 20, 9], "body"),
  E("chest", "body", [-6, 10, -13], [6, 21, -9], "body"),
  E("head", "head", [-4, 15, -19], [4, 23, -13], "head"),
  E("muzzle", "muzzle", [-3, 15, -22], [3, 19, -19], "head"),
  E("earL", "ear", [-4, 23, -16], [-2, 26, -13], "head"),
  E("earR", "ear", [2, 23, -16], [4, 26, -13], "head"),
  E("maneMain", "mane", [-8, 11, -15], [8, 27, -5], "head"),
  E("maneFrontL", "mane", [-7, 12, -20], [-3, 26, -15], "head"),
  E("maneFrontR", "mane", [3, 12, -20], [7, 26, -15], "head"),
  E("maneTop", "mane", [-4, 23, -20], [4, 27, -15], "head"),
  E("maneUnder", "mane", [-6, 7, -16], [6, 12, -6], "head"),
  E("tailBase", "tail", [-1.5, 16, 9], [1.5, 19, 14], "tail"),
  E("tailTuft", "tuft", [-2, 13, 13], [2, 18, 18], "tailEnd"),
  E("legFrontL", "leg", [-4.5, 0, -9], [-1.5, 10, -5], "legFrontL"),
  E("pawFrontL", "paw", [-5, 0, -10], [-1, 2, -4], "legFrontL"),
  E("legFrontR", "leg", [1.5, 0, -9], [4.5, 10, -5], "legFrontR"),
  E("pawFrontR", "paw", [1, 0, -10], [5, 2, -4], "legFrontR"),
  E("legBackL", "leg", [-4.5, 0, 4], [-1.5, 10, 8], "legBackL"),
  E("pawBackL", "paw", [-5, 0, 3], [-1, 2, 9], "legBackL"),
  E("legBackR", "leg", [1.5, 0, 4], [4.5, 10, 8], "legBackR"),
  E("pawBackR", "paw", [1, 0, 3], [5, 2, 9], "legBackR"),
];

const bones = [
  { name: "body", origin: [0, 15, 0], rotation: [0, 0, 0], parent: null },
  { name: "head", origin: [0, 17, -13], rotation: [0, 0, 0], parent: null },
  { name: "tail", origin: [0, 18, 9], rotation: [-40, 0, 0], parent: null },
  { name: "tailEnd", origin: [0, 16, 14], rotation: [0, 0, 0], parent: "tail" },
  { name: "legFrontL", origin: [-3, 10, -7], rotation: [0, 0, 0], parent: null },
  { name: "legFrontR", origin: [3, 10, -7], rotation: [0, 0, 0], parent: null },
  { name: "legBackL", origin: [-3, 10, 6], rotation: [0, 0, 0], parent: null },
  { name: "legBackR", origin: [3, 10, 6], rotation: [0, 0, 0], parent: null },
];

// deterministic uuid v4-ish
let seed = 0x9e3779b9;
const uuid = () => {
  const h = () => {
    seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
    return (seed >>> 0).toString(16).padStart(8, "0");
  };
  const s = (h() + h() + h() + h()).slice(0, 32);
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-4${s.slice(13, 16)}-a${s.slice(17, 20)}-${s.slice(20, 32)}`;
};

// --- UV packer: every face its own disjoint rect ---
const FACE_ORDER = ["north", "east", "south", "west", "up", "down"];
const faceSize = (w, h, d) => ({
  north: [w, h], south: [w, h], east: [d, h], west: [d, h], up: [w, d], down: [w, d],
});
let cx = 0, cy = 0, rowH = 0, maxY = 0;
const place = ([w, h]) => {
  if (cx + w > ATLAS_W) { cx = 0; cy += rowH; rowH = 0; }
  const r = [cx, cy, cx + w, cy + h];
  cx += w; rowH = Math.max(rowH, h); maxY = Math.max(maxY, cy + h);
  return r;
};

for (const el of elements) {
  const w = el.to[0] - el.from[0], h = el.to[1] - el.from[1], d = el.to[2] - el.from[2];
  if (![w, h, d].every(Number.isInteger)) throw new Error(`non-integer size on ${el.name}`);
  const sizes = faceSize(w, h, d);
  el.uuid = uuid();
  el.faces = {};
  for (const f of FACE_ORDER) el.faces[f] = { uv: place(sizes[f]), texture: 0 };
}
const ATLAS_H = Math.ceil((cy + rowH) / 2) * 2 || maxY;

// overlap check
const rects = [];
for (const el of elements) for (const f of FACE_ORDER) rects.push([el.name, f, el.faces[f].uv]);
for (let i = 0; i < rects.length; i++)
  for (let j = i + 1; j < rects.length; j++) {
    const [, , a] = rects[i], [, , b] = rects[j];
    if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3])
      throw new Error(`UV overlap: ${rects[i][0]}.${rects[i][1]} vs ${rects[j][0]}.${rects[j][1]}`);
  }

const boneUuid = Object.fromEntries(bones.map((b) => [b.name, uuid()]));

const groups = bones.map((b) => ({
  name: b.name, uuid: boneUuid[b.name], export: true, locked: false, scope: 0,
  selected: false, _static: { properties: {}, temp_data: {} },
  origin: b.origin, rotation: b.rotation, color: 0, children: [], reset: false,
  shade: true, mirror_uv: false, visibility: true, autouv: 0, isOpen: true, primary_selected: false,
}));

const childrenOf = (boneName) => elements.filter((e) => e.bone === boneName).map((e) => e.uuid);
const node = (boneName) => {
  const kids = bones.filter((b) => b.parent === boneName).map((b) => node(b.name));
  return { uuid: boneUuid[boneName], isOpen: true, children: [...childrenOf(boneName), ...kids] };
};
const outliner = bones.filter((b) => !b.parent).map((b) => node(b.name));

const bbEl = elements.map((el) => ({
  name: el.name, box_uv: false, render_order: "default", locked: false, export: true,
  scope: 0, allow_mirror_modeling: true, from: el.from, to: el.to, autouv: 0, color: 0,
  origin: [0, 0, 0], faces: el.faces, type: "cube", uuid: el.uuid,
}));

// 1x1 opaque placeholder; generate_texture.mjs overwrites textures[0].source
const PLACEHOLDER =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==";

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "lion",
  model_identifier: "",
  visible_box: [2, 2, 0],
  variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements: bbEl,
  groups,
  outliner,
  textures: [{
    name: "lion", path: "", folder: "", namespace: "", id: "0", group: "", scope: 0,
    width: ATLAS_W, height: ATLAS_H, uv_width: ATLAS_W, uv_height: ATLAS_H, particle: false,
    use_as_default: false, layers_enabled: false, sync_to_project: "", file_format: "png",
    render_mode: "default", render_sides: "auto", wrap_mode: "limited", pbr_channel: "color",
    fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "", frame_interpolate: false,
    visible: true, internal: true, saved: false, uuid: uuid(), source: PLACEHOLDER,
  }],
};

writeFileSync(join(DIR, "lion.bbmodel"), JSON.stringify(model));
console.log(`lion.bbmodel written: ${elements.length} elements, atlas ${ATLAS_W}x${ATLAS_H}, no UV overlap`);
