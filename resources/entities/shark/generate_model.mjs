// Generates shark.bbmodel geometry + a disjoint per-face UV atlas.
// Texture pixels are painted separately by generate_texture.mjs.
// Run: make dc CMD="node resources/entities/shark/generate_model.mjs"
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 160;

// name -> { from, to }
const cuboids = [
  { name: "body", from: [-6, 4, -8], to: [6, 18, 14] },
  { name: "head", from: [-5, 5, -16], to: [5, 16, -8] },
  { name: "snout", from: [-3, 6, -21], to: [3, 12, -16] },
  { name: "dorsalFin", from: [-1, 18, -4], to: [1, 30, 6] },
  { name: "pectoralFinL", from: [-16, 4, -6], to: [-6, 5, 2] },
  { name: "pectoralFinR", from: [6, 4, -6], to: [16, 5, 2] },
  { name: "peduncle", from: [-3, 6, 14], to: [3, 14, 22] },
  { name: "caudalUpper", from: [-1, 12, 22], to: [1, 32, 30] },
  { name: "caudalLower", from: [-1, 2, 22], to: [1, 12, 28] },
];

const FACES = ["north", "east", "south", "west", "up", "down"];
function faceSize(sz, face) {
  const [w, h, d] = sz;
  switch (face) {
    case "north": case "south": return [w, h];
    case "east": case "west": return [d, h];
    case "up": case "down": return [w, d];
  }
}

let cx = 0, cy = 0, rowH = 0;
function place(w, h) {
  if (cx + w > ATLAS_W) { cx = 0; cy += rowH; rowH = 0; }
  const rect = [cx, cy, cx + w, cy + h];
  cx += w; rowH = Math.max(rowH, h);
  return rect;
}

const elements = cuboids.map((c) => {
  const sz = [c.to[0] - c.from[0], c.to[1] - c.from[1], c.to[2] - c.from[2]];
  for (const a of sz) if (!Number.isInteger(a)) throw new Error(`${c.name} non-integer size ${sz}`);
  const faces = {};
  for (const f of FACES) {
    const [w, h] = faceSize(sz, f);
    faces[f] = { uv: place(w, h), texture: 0 };
  }
  return {
    name: c.name, box_uv: false, render_order: "default", locked: false, export: true,
    scope: 0, allow_mirror_modeling: false, from: c.from, to: c.to, autouv: 0, color: 0,
    origin: [0, 0, 0], faces, type: "cube", uuid: randomUUID(),
  };
});
const ATLAS_H = cy + rowH;

const rects = [];
for (const e of elements) for (const f of FACES) rects.push([e.name, f, e.faces[f].uv]);
for (let i = 0; i < rects.length; i++) for (let j = i + 1; j < rects.length; j++) {
  const [a, b] = [rects[i][2], rects[j][2]];
  if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3])
    throw new Error(`UV overlap ${rects[i][0]}.${rects[i][1]} vs ${rects[j][0]}.${rects[j][1]}`);
}

function bone(name, origin, childBones, childElems) {
  return {
    name, uuid: randomUUID(), export: true, locked: false, scope: 0, selected: false,
    _static: { properties: {}, temp_data: {} }, origin, rotation: [0, 0, 0], color: 0,
    children: [], reset: false, shade: true, mirror_uv: false, visibility: true,
    autouv: 0, isOpen: true, primary_selected: false,
    _childBones: childBones, _childElems: childElems,
  };
}
const el = (n) => elements.find((e) => e.name === n).uuid;

const bones = [
  bone("body", [0, 11, 0], ["head", "dorsalFin", "pectoralFinL", "pectoralFinR", "peduncle"], ["body"]),
  bone("head", [0, 11, -8], ["snout"], ["head"]),
  bone("snout", [0, 9, -16], [], ["snout"]),
  bone("dorsalFin", [0, 18, 0], [], ["dorsalFin"]),
  bone("pectoralFinL", [-6, 5, -4], [], ["pectoralFinL"]),
  bone("pectoralFinR", [6, 5, -4], [], ["pectoralFinR"]),
  bone("peduncle", [0, 10, 14], ["caudalUpper", "caudalLower"], ["peduncle"]),
  bone("caudalUpper", [0, 12, 22], [], ["caudalUpper"]),
  bone("caudalLower", [0, 12, 22], [], ["caudalLower"]),
];
const B = (n) => bones.find((b) => b.name === n);
function outNode(b) {
  return {
    uuid: b.uuid, isOpen: true,
    children: [...b._childElems.map(el), ...b._childBones.map((n) => outNode(B(n)))],
  };
}
const outliner = [outNode(B("body"))];
const groups = bones.map(({ _childBones, _childElems, ...g }) => g);

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "shark", model_identifier: "", visible_box: [1, 1, 0], variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements, groups, outliner,
  textures: [{
    name: "shark", path: "", folder: "", namespace: "", id: "0", group: "",
    scope: 0, width: ATLAS_W, height: ATLAS_H, uv_width: ATLAS_W, uv_height: ATLAS_H,
    particle: false, use_as_default: false, layers_enabled: false, sync_to_project: "",
    file_format: "png", render_mode: "default", render_sides: "auto", wrap_mode: "limited",
    pbr_channel: "color", fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "",
    frame_interpolate: false, visible: true, internal: true, saved: false, uuid: randomUUID(),
    source: "data:image/png;base64,",
  }],
};

writeFileSync(new URL("./shark.bbmodel", import.meta.url), JSON.stringify(model));
console.log(`wrote shark.bbmodel  atlas ${ATLAS_W}x${ATLAS_H}  ${elements.length} elements  rects OK`);
