// Generates pterodactyl.bbmodel geometry + a disjoint per-face UV atlas.
// Texture pixels are painted separately by generate_texture.mjs.
// Run: make dc CMD="node resources/entities/pterodactyl/generate_model.mjs"
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 220;

// pterosaur facing -Z. long membrane wings, backward rod crest, thin whip tail.
const cuboids = [
  { name: "body", from: [-3, 6, -6], to: [3, 13, 6] },
  { name: "neck", from: [-2, 10, -10], to: [2, 13, -6] },
  { name: "head", from: [-2, 11, -14], to: [2, 15, -10] },
  { name: "beak", from: [-1.5, 11, -26], to: [1.5, 13, -14] },
  { name: "crestRod", from: [-0.5, 15, -12], to: [0.5, 17, 2] },
  { name: "crestTip", from: [-1.5, 15, 2], to: [1.5, 18, 6] },
  { name: "wing0", from: [-22, 10, -3], to: [-3, 11, 7] },
  { name: "wing0tip", from: [-40, 10, -1], to: [-22, 11, 6] },
  { name: "wing1", from: [3, 10, -3], to: [22, 11, 7] },
  { name: "wing1tip", from: [22, 10, -1], to: [40, 11, 6] },
  { name: "clawL", from: [-24, 10, -6], to: [-22, 12, -3] },
  { name: "clawR", from: [22, 10, -6], to: [24, 12, -3] },
  { name: "leg0", from: [-3, 1, 4], to: [-1, 7, 6] },
  { name: "leg1", from: [1, 1, 4], to: [3, 7, 6] },
  { name: "tail", from: [-0.5, 8, 6], to: [0.5, 9, 22] },
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
  bone("body", [0, 10, 0], ["head", "wing0", "wing1", "leg0", "leg1", "tail"], ["body", "neck"]),
  bone("head", [0, 12, -10], ["beak", "crestRod"], ["head"]),
  bone("beak", [0, 12, -14], [], ["beak"]),
  bone("crestRod", [0, 16, -12], ["crestTip"], ["crestRod"]),
  bone("crestTip", [0, 16, 2], [], ["crestTip"]),
  bone("wing0", [-3, 10, 2], ["wing0tip", "clawL"], ["wing0"]),
  bone("wing0tip", [-22, 10, 2], [], ["wing0tip"]),
  bone("clawL", [-23, 11, -4], [], ["clawL"]),
  bone("wing1", [3, 10, 2], ["wing1tip", "clawR"], ["wing1"]),
  bone("wing1tip", [22, 10, 2], [], ["wing1tip"]),
  bone("clawR", [23, 11, -4], [], ["clawR"]),
  bone("leg0", [-2, 7, 5], [], ["leg0"]),
  bone("leg1", [2, 7, 5], [], ["leg1"]),
  bone("tail", [0, 8, 6], [], ["tail"]),
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
  name: "pterodactyl", model_identifier: "", visible_box: [1, 1, 0], variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements, groups, outliner,
  textures: [{
    name: "pterodactyl", path: "", folder: "", namespace: "", id: "0", group: "",
    scope: 0, width: ATLAS_W, height: ATLAS_H, uv_width: ATLAS_W, uv_height: ATLAS_H,
    particle: false, use_as_default: false, layers_enabled: false, sync_to_project: "",
    file_format: "png", render_mode: "default", render_sides: "auto", wrap_mode: "limited",
    pbr_channel: "color", fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "",
    frame_interpolate: false, visible: true, internal: true, saved: false, uuid: randomUUID(),
    source: "data:image/png;base64,",
  }],
};

writeFileSync(new URL("./pterodactyl.bbmodel", import.meta.url), JSON.stringify(model));
console.log(`wrote pterodactyl.bbmodel  atlas ${ATLAS_W}x${ATLAS_H}  ${elements.length} elements  rects OK`);
