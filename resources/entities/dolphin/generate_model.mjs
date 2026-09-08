// Generates dolphin.bbmodel geometry + a disjoint per-face UV atlas.
// Texture pixels are painted separately by generate_texture.mjs.
// Run: make dc CMD="node resources/entities/dolphin/generate_model.mjs"
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 128;

// name -> { from:[x,y,z], to:[x,y,z], material }
const cuboids = [
  { name: "body", from: [-4, 6, -6], to: [4, 17, 10], material: "skin" },
  { name: "head", from: [-3.5, 7, -14], to: [3.5, 15, -6], material: "skin" },
  { name: "rostrum", from: [-1.5, 8.5, -20], to: [1.5, 11.5, -14], material: "skin" },
  { name: "dorsalFin", from: [-0.5, 17, -1], to: [0.5, 24, 6], material: "fin" },
  { name: "pectoralFinL", from: [-9, 7, -6], to: [-4, 8, 0], material: "fin" },
  { name: "pectoralFinR", from: [4, 7, -6], to: [9, 8, 0], material: "fin" },
  { name: "peduncle", from: [-2, 8, 10], to: [2, 14, 18], material: "skin" },
  { name: "tailFluke", from: [-9, 10, 18], to: [9, 11, 24], material: "fin" },
];

const FACES = ["north", "east", "south", "west", "up", "down"];

// face -> [w,h] taken from cuboid size
function faceSize(sz, face) {
  const [w, h, d] = sz;
  switch (face) {
    case "north": case "south": return [w, h];
    case "east": case "west": return [d, h];
    case "up": case "down": return [w, d];
  }
}

// simple shelf packer -> disjoint rects by construction
let cx = 0, cy = 0, rowH = 0;
function place(w, h) {
  if (cx + w > ATLAS_W) { cx = 0; cy += rowH; rowH = 0; }
  const rect = [cx, cy, cx + w, cy + h];
  cx += w;
  rowH = Math.max(rowH, h);
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

// verify no two face-rects overlap
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
  bone("head", [0, 11, -6], ["rostrum"], ["head"]),
  bone("rostrum", [0, 10, -14], [], ["rostrum"]),
  bone("dorsalFin", [0, 17, 2], [], ["dorsalFin"]),
  bone("pectoralFinL", [-4, 8, -4], [], ["pectoralFinL"]),
  bone("pectoralFinR", [4, 8, -4], [], ["pectoralFinR"]),
  bone("peduncle", [0, 11, 10], ["tailFluke"], ["peduncle"]),
  bone("tailFluke", [0, 10.5, 18], [], ["tailFluke"]),
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
  name: "dolphin", model_identifier: "", visible_box: [1, 1, 0], variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements, groups, outliner,
  textures: [{
    name: "dolphin", path: "", folder: "", namespace: "", id: "0", group: "",
    scope: 0, width: ATLAS_W, height: ATLAS_H, uv_width: ATLAS_W, uv_height: ATLAS_H,
    particle: false, use_as_default: false, layers_enabled: false, sync_to_project: "",
    file_format: "png", render_mode: "default", render_sides: "auto", wrap_mode: "limited",
    pbr_channel: "color", fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "",
    frame_interpolate: false, visible: true, internal: true, saved: false, uuid: randomUUID(),
    source: "data:image/png;base64,",
  }],
};

const out = new URL("./dolphin.bbmodel", import.meta.url);
writeFileSync(out, JSON.stringify(model));
console.log(`wrote dolphin.bbmodel  atlas ${ATLAS_W}x${ATLAS_H}  ${elements.length} elements  rects OK`);
