// Generates resources/entities/pig/pig.bbmodel geometry (elements/groups/outliner/texture stub).
// Per-face non-overlapping UV rects computed by a simple row packer. Run:
//   make dc CMD="node resources/entities/pig/generate_model.mjs"
// Then paint the texture with generate_texture.mjs.
import { writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";

const ATLAS_W = 128;

// name, from, to  (pixel units, 16px = 1 block, feet at y=0)
const cuboids = [
  { name: "body", from: [-4, 7, -7], to: [4, 15, 8] },
  { name: "head", from: [-3.5, 8, -14], to: [3.5, 15, -7] },
  { name: "snout", from: [-2, 8, -16], to: [2, 11, -14] },
  { name: "ear_left", from: [-4.5, 15, -12], to: [-1.5, 19, -10] },
  { name: "ear_right", from: [1.5, 15, -12], to: [4.5, 19, -10] },
  { name: "leg_front_left", from: [-4, 0, -6], to: [-1, 7, -3] },
  { name: "leg_front_right", from: [1, 0, -6], to: [4, 7, -3] },
  { name: "leg_back_left", from: [-4, 0, 4], to: [-1, 7, 7] },
  { name: "leg_back_right", from: [1, 0, 4], to: [4, 7, 7] },
  { name: "tail", from: [1, 12, 8], to: [3, 15, 10] },
];

// bone -> {origin, rotation, parent}
const bones = {
  body: { origin: [0, 11, 0], rotation: [0, 0, 0], parent: null },
  head: { origin: [0, 12, -7], rotation: [0, 0, 0], parent: "body" },
  snout: { origin: [0, 10, -14], rotation: [0, 0, 0], parent: "head" },
  ear_left: { origin: [-2, 15, -11], rotation: [0, 0, -18], parent: "head" },
  ear_right: { origin: [2, 15, -11], rotation: [0, 0, 18], parent: "head" },
  leg_front_left: { origin: [-2, 7, -4], rotation: [0, 0, 0], parent: "body" },
  leg_front_right: { origin: [2, 7, -4], rotation: [0, 0, 0], parent: "body" },
  leg_back_left: { origin: [-2, 7, 5], rotation: [0, 0, 0], parent: "body" },
  leg_back_right: { origin: [2, 7, 5], rotation: [0, 0, 0], parent: "body" },
  tail: { origin: [1, 13, 8], rotation: [-40, 0, 0], parent: "body" },
};

// ---- UV packer -------------------------------------------------------------
let cx = 0;
let cy = 0;
let rowH = 0;
function place(w, h) {
  if (cx + w > ATLAS_W) {
    cx = 0;
    cy += rowH;
    rowH = 0;
  }
  const rect = [cx, cy, cx + w, cy + h];
  cx += w;
  rowH = Math.max(rowH, h);
  return rect;
}

const elements = cuboids.map((c) => {
  const [fx, fy, fz] = c.from;
  const [tx, ty, tz] = c.to;
  const dx = Math.round(tx - fx);
  const dy = Math.round(ty - fy);
  const dz = Math.round(tz - fz);
  if (tx - fx !== dx || ty - fy !== dy || tz - fz !== dz) {
    throw new Error(`non-integer size on ${c.name}`);
  }
  const faces = {
    north: { uv: place(dx, dy), texture: 0 },
    east: { uv: place(dz, dy), texture: 0 },
    south: { uv: place(dx, dy), texture: 0 },
    west: { uv: place(dz, dy), texture: 0 },
    up: { uv: place(dx, dz), texture: 0 },
    down: { uv: place(dx, dz), texture: 0 },
  };
  return {
    name: c.name,
    box_uv: false,
    render_order: "default",
    locked: false,
    export: true,
    allow_mirror_modeling: false,
    from: c.from,
    to: c.to,
    autouv: 0,
    color: 0,
    origin: [0, 0, 0],
    faces,
    type: "cube",
    uuid: randomUUID(),
  };
});

const atlasH = cy + rowH;

// overlap check
const rects = [];
for (const e of elements) for (const f of Object.values(e.faces)) rects.push(f.uv);
for (let i = 0; i < rects.length; i++)
  for (let j = i + 1; j < rects.length; j++) {
    const [ax1, ay1, ax2, ay2] = rects[i];
    const [bx1, by1, bx2, by2] = rects[j];
    if (ax1 < bx2 && bx1 < ax2 && ay1 < by2 && by1 < ay2)
      throw new Error(`UV overlap ${i} ${j}`);
  }

const elByName = Object.fromEntries(elements.map((e) => [e.name, e]));
const groups = {};
for (const [name, b] of Object.entries(bones)) {
  groups[name] = {
    name,
    uuid: randomUUID(),
    export: true,
    locked: false,
    selected: false,
    origin: b.origin,
    rotation: b.rotation,
    color: 0,
    children: [],
    reset: false,
    shade: true,
    mirror_uv: false,
    visibility: true,
    autouv: 0,
    isOpen: true,
  };
}

function outNode(name) {
  const b = bones[name];
  const childBones = Object.keys(bones).filter((n) => bones[n].parent === name);
  return {
    uuid: groups[name].uuid,
    name,
    isOpen: true,
    children: [elByName[name].uuid, ...childBones.map(outNode)],
  };
}
// nested groups.children too (Blockbench keeps both)
for (const [name, g] of Object.entries(groups)) {
  const childBones = Object.keys(bones).filter((n) => bones[n].parent === name);
  g.children = [elByName[name].uuid, ...childBones.map((n) => groups[n])];
}
const outliner = Object.keys(bones)
  .filter((n) => bones[n].parent === null)
  .map(outNode);

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "pig",
  resolution: { width: ATLAS_W, height: atlasH },
  elements,
  groups: Object.values(groups),
  outliner,
  textures: [
    {
      name: "pig",
      path: "",
      folder: "",
      namespace: "",
      id: "0",
      width: ATLAS_W,
      height: atlasH,
      uv_width: ATLAS_W,
      uv_height: atlasH,
      particle: false,
      use_as_default: false,
      internal: true,
      saved: false,
      file_format: "png",
      render_mode: "default",
      render_sides: "auto",
      visible: true,
      uuid: randomUUID(),
      source:
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    },
  ],
};

const out = new URL("./pig.bbmodel", import.meta.url);
writeFileSync(out, JSON.stringify(model));
console.log(`wrote pig.bbmodel  atlas ${ATLAS_W}x${atlasH}  ${elements.length} elements`);
