// Generates resources/entities/elephant/elephant.bbmodel geometry + disjoint UV atlas.
// STRICT quadruped-skeleton structure: exactly the 7 skeleton groups
// (body, head, tail, frontLegL, frontLegR, backLegL, backLegR), all flat at the outliner root.
// NO extra groups, NO nested groups. Elephant identity is added only as extra CUBOIDS parented
// into the existing groups:
//   body <- hump          head <- earL, earR, tuskL, tuskR, trunk0..trunk3
// walkBoneAliases in the yaml wire front/back legs to the standard walk bones.
// Texture painted by the sibling generate_texture.mjs.
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 128;

// the 7 skeleton groups (same names/rotations as quadruped_skeleton.bbmodel; origins scaled to
// elephant proportions — the structure, not the pivot values, is what must match the skeleton)
const GROUPS = [
  { name: "body", origin: [0, 18, 0], rotation: [0, 0, 0] },
  { name: "head", origin: [0, 30, -16], rotation: [0, 0, 0] },
  { name: "tail", origin: [0, 32, 16], rotation: [-25, 0, 0] },
  { name: "frontLegL", origin: [-9, 20, -10], rotation: [0, 0, 0] },
  { name: "frontLegR", origin: [9, 20, -10], rotation: [0, 0, 0] },
  { name: "backLegL", origin: [-9, 20, 10], rotation: [0, 0, 0] },
  { name: "backLegR", origin: [9, 20, 10], rotation: [0, 0, 0] },
];

// group = which skeleton group owns this cuboid. mat = skin material for the texture generator.
const ELEMENTS = [
  { name: "body", group: "body", from: [-13, 18, -16], to: [13, 40, 14], mat: "body" },
  { name: "hump", group: "body", from: [-11, 38, -14], to: [11, 46, 2], mat: "body" },

  { name: "head", group: "head", from: [-9, 24, -28], to: [9, 40, -14], mat: "head" },
  { name: "earL", group: "head", from: [-16, 26, -22], to: [-9, 42, -19], mat: "ear" },
  { name: "earR", group: "head", from: [9, 26, -22], to: [16, 42, -19], mat: "ear" },
  { name: "tuskL", group: "head", from: [-7, 20, -35], to: [-4, 25, -27], mat: "tusk" },
  { name: "tuskR", group: "head", from: [4, 20, -35], to: [7, 25, -27], mat: "tusk" },
  { name: "trunk0", group: "head", from: [-4, 20, -33], to: [4, 30, -25], mat: "trunk" },
  { name: "trunk1", group: "head", from: [-3, 10, -34], to: [3, 20, -26], mat: "trunk" },
  { name: "trunk2", group: "head", from: [-3, 3, -33], to: [3, 10, -27], mat: "trunk" },
  { name: "trunk3", group: "head", from: [-2, 0, -34], to: [2, 5, -28], mat: "trunk" },

  { name: "tail", group: "tail", from: [-2, 16, 14], to: [2, 34, 18], mat: "body" },

  { name: "frontLegL", group: "frontLegL", from: [-13, 0, -14], to: [-5, 20, -6], mat: "leg" },
  { name: "frontLegR", group: "frontLegR", from: [5, 0, -14], to: [13, 20, -6], mat: "leg" },
  { name: "backLegL", group: "backLegL", from: [-13, 0, 6], to: [-5, 20, 14], mat: "leg" },
  { name: "backLegR", group: "backLegR", from: [5, 0, 6], to: [13, 20, 14], mat: "leg" },
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

const outliner = GROUPS.map((g) => ({
  uuid: uuids["gr:" + g.name],
  isOpen: true,
  children: ELEMENTS.filter((e) => e.group === g.name).map((e) => uuids["el:" + e.name]),
}));

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "elephant", model_identifier: "", visible_box: [1, 1, 0], variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements, groups, outliner,
  textures: [{
    name: "elephant", path: "", folder: "", namespace: "", id: "0", group: "", scope: 0,
    width: ATLAS_W, height: ATLAS_H, uv_width: ATLAS_W, uv_height: ATLAS_H, particle: false,
    use_as_default: false, layers_enabled: false, sync_to_project: "", file_format: "png",
    render_mode: "default", render_sides: "auto", wrap_mode: "limited", pbr_channel: "color",
    fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "", frame_interpolate: false,
    visible: true, internal: true, saved: false, uuid: randomUUID(),
    source: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  }],
};

writeFileSync(new URL("./elephant.bbmodel", import.meta.url), JSON.stringify(model));
console.log(`wrote elephant.bbmodel  atlas ${ATLAS_W}x${ATLAS_H}  elements ${elements.length}  groups ${groups.length} (flat)`);
