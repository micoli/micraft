// Generates squid.bbmodel geometry + a disjoint per-face UV atlas.
// Texture pixels are painted separately by generate_texture.mjs.
// Run: make dc CMD="node resources/entities/squid/generate_model.mjs"
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 160;

// front = -Z (head + arms lead), mantle trails to +Z, terminal fins at the tail.
const cuboids = [
  { name: "mantleFront", from: [-4, 4, -6], to: [4, 12, 6] },
  { name: "mantleRear", from: [-3, 5, 6], to: [3, 11, 16] },
  { name: "posteriorTip", from: [-1.5, 6.5, 16], to: [1.5, 9.5, 20] },
  { name: "finL", from: [-10, 6, 10], to: [-3, 8, 20] },
  { name: "finR", from: [3, 6, 10], to: [10, 8, 20] },
  { name: "head", from: [-3.5, 4.5, -11], to: [3.5, 10.5, -6] },
  { name: "eyeL", from: [-4.5, 6, -10], to: [-3.5, 9, -7] },
  { name: "eyeR", from: [3.5, 6, -10], to: [4.5, 9, -7] },
  { name: "arm0", from: [-3, 7, -21], to: [-1, 9, -11] },
  { name: "arm1", from: [1, 7, -21], to: [3, 9, -11] },
  { name: "arm2", from: [-4, 4, -21], to: [-2, 6, -11] },
  { name: "arm3", from: [2, 4, -21], to: [4, 6, -11] },
  { name: "arm4", from: [-2, 2, -21], to: [0, 4, -11] },
  { name: "arm5", from: [0, 2, -21], to: [2, 4, -11] },
  { name: "tentacleL", from: [-5, 5, -31], to: [-3, 7, -11] },
  { name: "tentacleR", from: [3, 5, -31], to: [5, 7, -11] },
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

const armNames = ["arm0", "arm1", "arm2", "arm3", "arm4", "arm5"];
const bones = [
  bone("body", [0, 8, 2], ["head", "finL", "finR"], ["mantleFront", "mantleRear", "posteriorTip"]),
  bone("head", [0, 8, -8], ["tentacleL", "tentacleR", ...armNames], ["head", "eyeL", "eyeR"]),
  bone("finL", [-3, 7, 10], [], ["finL"]),
  bone("finR", [3, 7, 10], [], ["finR"]),
  bone("tentacleL", [-4, 6, -11], [], ["tentacleL"]),
  bone("tentacleR", [4, 6, -11], [], ["tentacleR"]),
  ...armNames.map((n) => {
    const c = cuboids.find((x) => x.name === n);
    return bone(n, [(c.from[0] + c.to[0]) / 2, (c.from[1] + c.to[1]) / 2, -11], [], [n]);
  }),
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
  name: "squid", model_identifier: "", visible_box: [1, 1, 0], variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements, groups, outliner,
  textures: [{
    name: "squid", path: "", folder: "", namespace: "", id: "0", group: "",
    scope: 0, width: ATLAS_W, height: ATLAS_H, uv_width: ATLAS_W, uv_height: ATLAS_H,
    particle: false, use_as_default: false, layers_enabled: false, sync_to_project: "",
    file_format: "png", render_mode: "default", render_sides: "auto", wrap_mode: "limited",
    pbr_channel: "color", fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "",
    frame_interpolate: false, visible: true, internal: true, saved: false, uuid: randomUUID(),
    source: "data:image/png;base64,",
  }],
};

writeFileSync(new URL("./squid.bbmodel", import.meta.url), JSON.stringify(model));
console.log(`wrote squid.bbmodel  atlas ${ATLAS_W}x${ATLAS_H}  ${elements.length} elements  rects OK`);
