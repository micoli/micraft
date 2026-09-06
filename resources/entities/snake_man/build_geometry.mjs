// Generates resources/entities/snake_man/snake_man.bbmodel geometry + disjoint UV atlas.
// STRICT humanoid-skeleton structure: exactly the 6 skeleton groups
// (body, head, rightArm, leftArm, rightLeg, leftLeg), all flat at the outliner root, same
// group origins as humanoid_skeleton.bbmodel. NO extra groups, NO nested groups.
// Snake identity is added only as extra CUBOIDS parented into the existing groups:
//   head  <- snout, hood      body <- tailA, tailB
// Earless by design — snakes have no external ears.
// Texture painted by the sibling generate_texture.mjs.
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 64;

// the 6 skeleton groups, verbatim origins from humanoid_skeleton.bbmodel
const GROUPS = [
  { name: "body", origin: [0, 12, 0], rotation: [0, 0, 0] },
  { name: "head", origin: [0, 24, 0], rotation: [0, 0, 0] },
  { name: "rightArm", origin: [-4, 24, 0], rotation: [0, 0, 0] },
  { name: "leftArm", origin: [4, 24, 0], rotation: [0, 0, 0] },
  { name: "rightLeg", origin: [-2, 12, 0], rotation: [0, 0, 0] },
  { name: "leftLeg", origin: [2, 12, 0], rotation: [0, 0, 0] },
];

// group = which skeleton group owns this cuboid. mat = skin material for the texture generator.
const ELEMENTS = [
  { name: "body", group: "body", from: [-4, 12, -2], to: [4, 24, 2], mat: "scale" },
  { name: "tailA", group: "body", from: [-2, 10, 2], to: [2, 14, 9], mat: "belly" },
  { name: "tailB", group: "body", from: [-1, 10, 9], to: [1, 14, 16], mat: "belly" },
  { name: "head", group: "head", from: [-4, 24, -4], to: [4, 32, 4], mat: "scale" },
  { name: "snout", group: "head", from: [-2, 25, -9], to: [2, 29, -4], mat: "scale" },
  { name: "hood", group: "head", from: [-7, 23, 4], to: [7, 35, 6], mat: "hood" },
  { name: "rightArm", group: "rightArm", from: [-8, 12, -2], to: [-4, 24, 2], mat: "scale" },
  { name: "leftArm", group: "leftArm", from: [4, 12, -2], to: [8, 24, 2], mat: "scale" },
  { name: "rightLeg", group: "rightLeg", from: [-4, 0, -2], to: [0, 12, 2], mat: "scale" },
  { name: "leftLeg", group: "leftLeg", from: [0, 0, -2], to: [4, 12, 2], mat: "scale" },
];

function faceSizes(el) {
  const dx = el.to[0] - el.from[0], dy = el.to[1] - el.from[1], dz = el.to[2] - el.from[2];
  return { north: [dx, dy], south: [dx, dy], east: [dz, dy], west: [dz, dy], up: [dx, dz], down: [dx, dz] };
}
let cx = 0, cy = 0, rowH = 0;
const uvOf = {};
for (const el of ELEMENTS) {
  const s = faceSizes(el);
  uvOf[el.name] = {};
  for (const f of ["north", "east", "south", "west", "up", "down"]) {
    const [w, h] = s[f];
    if (!Number.isInteger(w) || !Number.isInteger(h)) throw new Error(`non-int face ${el.name}.${f}`);
    if (cx + w > ATLAS_W) { cx = 0; cy += rowH; rowH = 0; }
    uvOf[el.name][f] = [cx, cy, cx + w, cy + h];
    cx += w; rowH = Math.max(rowH, h);
  }
}
const ATLAS_H = cy + rowH;

const rects = [];
for (const n of Object.keys(uvOf)) for (const f of Object.keys(uvOf[n])) rects.push([n + "." + f, uvOf[n][f]]);
for (let i = 0; i < rects.length; i++) for (let j = i + 1; j < rects.length; j++) {
  const [ax1, ay1, ax2, ay2] = rects[i][1], [bx1, by1, bx2, by2] = rects[j][1];
  if (ax1 < bx2 && bx1 < ax2 && ay1 < by2 && by1 < ay2) throw new Error(`UV overlap ${rects[i][0]} vs ${rects[j][0]}`);
}

const uuids = {};
for (const el of ELEMENTS) uuids["el:" + el.name] = randomUUID();
for (const g of GROUPS) uuids["gr:" + g.name] = randomUUID();

const elements = ELEMENTS.map((el) => ({
  name: el.name, box_uv: false, render_order: "default", locked: false, export: true, scope: 0,
  allow_mirror_modeling: false, from: el.from, to: el.to, autouv: 0, color: 0, origin: [0, 0, 0],
  faces: Object.fromEntries(["north", "east", "south", "west", "up", "down"].map((f) => [f, { uv: uvOf[el.name][f], texture: 0 }])),
  type: "cube", uuid: uuids["el:" + el.name],
}));

const groups = GROUPS.map((g) => ({
  name: g.name, uuid: uuids["gr:" + g.name], export: true, locked: false, scope: 0, selected: false,
  _static: { properties: {}, temp_data: {} }, origin: g.origin, rotation: g.rotation, color: 0,
  children: [], reset: false, shade: true, mirror_uv: false, visibility: true, autouv: 0,
  isOpen: true, primary_selected: false,
}));

// flat outliner: one node per skeleton group, children = its cuboids (uuid strings)
const outliner = GROUPS.map((g) => ({
  uuid: uuids["gr:" + g.name],
  isOpen: true,
  children: ELEMENTS.filter((e) => e.group === g.name).map((e) => uuids["el:" + e.name]),
}));

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "snake_man", model_identifier: "", visible_box: [1, 1, 0], variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements, groups, outliner,
  textures: [{
    name: "snake_man", path: "", folder: "", namespace: "", id: "0", group: "", scope: 0,
    width: ATLAS_W, height: ATLAS_H, uv_width: ATLAS_W, uv_height: ATLAS_H, particle: false,
    use_as_default: false, layers_enabled: false, sync_to_project: "", file_format: "png",
    render_mode: "default", render_sides: "auto", wrap_mode: "limited", pbr_channel: "color",
    fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "", frame_interpolate: false,
    visible: true, internal: true, saved: false, uuid: randomUUID(),
    source: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  }],
};

writeFileSync(new URL("./snake_man.bbmodel", import.meta.url), JSON.stringify(model));
console.log(`wrote snake_man.bbmodel  atlas ${ATLAS_W}x${ATLAS_H}  elements ${elements.length}  groups ${groups.length} (flat)`);
