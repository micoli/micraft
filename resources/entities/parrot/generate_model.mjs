// Generates parrot.bbmodel geometry + per-face packed UVs (box_uv:false everywhere).
// Texture pixels are painted separately by generate_texture.mjs.
// Run: make dc CMD="node resources/entities/parrot/generate_model.mjs"

import { writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ATLAS_W = 128;

// name, from, to  (pixel units, 16px = 1 block). size (to-from) is integer on every axis.
const CUBES = [
  { name: "body", from: [-3, 4, -4], to: [3, 10, 4] },
  { name: "head", from: [-2.5, 9, -8], to: [2.5, 14, -3] },
  { name: "upperBeak", from: [-1.5, 10, -10], to: [1.5, 12, -8] },
  { name: "lowerBeak", from: [-1.5, 9, -9], to: [1.5, 10, -8] },
  { name: "tail0", from: [-2, 5, 4], to: [2, 8, 12] },
  { name: "tail1", from: [-1.5, 5, 12], to: [1.5, 7, 22] },
  { name: "wing0", from: [-14, 7, -3], to: [-3, 10, 4] },
  { name: "wing1", from: [3, 7, -3], to: [14, 10, 4] },
  { name: "leg0", from: [-2, 0, -1], to: [0, 4, 1] },
  { name: "leg1", from: [0, 0, -1], to: [2, 4, 1] },
  { name: "foot0", from: [-2, 0, -2], to: [0, 1, 1] },
  { name: "foot1", from: [0, 0, -2], to: [2, 1, 1] },
];

// bone name -> { origin, cubes:[names], parent }
const BONES = [
  { name: "body", origin: [0, 7, 0], cubes: ["body"], parent: null },
  { name: "head", origin: [0, 10, -3], cubes: ["head", "upperBeak", "lowerBeak"], parent: "body" },
  { name: "tail", origin: [0, 7, 4], cubes: ["tail0", "tail1"], parent: "body" },
  { name: "wing0", origin: [-3, 9, 0], cubes: ["wing0"], parent: "body" },
  { name: "wing1", origin: [3, 9, 0], cubes: ["wing1"], parent: "body" },
  { name: "leg0", origin: [-1, 4, 0], cubes: ["leg0", "foot0"], parent: "body" },
  { name: "leg1", origin: [1, 4, 0], cubes: ["leg1", "foot1"], parent: "body" },
];

const faceSize = (c) => {
  const [dx, dy, dz] = [0, 1, 2].map((i) => c.to[i] - c.from[i]);
  return {
    north: [dx, dy], south: [dx, dy],
    east: [dz, dy], west: [dz, dy],
    up: [dx, dz], down: [dx, dz],
  };
};

// row packer: guarantees disjoint rects by construction
let cx = 0, cy = 0, rowH = 0;
const packRect = (w, h) => {
  if (cx + w > ATLAS_W) { cx = 0; cy += rowH; rowH = 0; }
  const rect = [cx, cy, cx + w, cy + h];
  cx += w;
  rowH = Math.max(rowH, h);
  return rect;
};

const elements = CUBES.map((c) => {
  const sizes = faceSize(c);
  const faces = {};
  for (const f of ["north", "east", "south", "west", "up", "down"]) {
    faces[f] = { uv: packRect(sizes[f][0], sizes[f][1]), texture: 0 };
  }
  return {
    name: c.name,
    box_uv: false,
    render_order: "default",
    locked: false,
    export: true,
    scope: 0,
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
const elByName = Object.fromEntries(elements.map((e) => [e.name, e]));

const ATLAS_H = cy + rowH;

const groups = BONES.map((b) => ({
  name: b.name,
  uuid: randomUUID(),
  export: true,
  locked: false,
  scope: 0,
  selected: false,
  _static: { properties: {}, temp_data: {} },
  origin: b.origin,
  rotation: [0, 0, 0],
  color: 0,
  children: [],
  reset: false,
  shade: true,
  mirror_uv: false,
  visibility: true,
  autouv: 0,
  isOpen: true,
  primary_selected: false,
}));
const boneByName = Object.fromEntries(groups.map((g, i) => [BONES[i].name, { g, def: BONES[i] }]));

const outlinerNode = (name) => {
  const { g, def } = boneByName[name];
  const children = [
    ...def.cubes.map((cn) => elByName[cn].uuid),
    ...BONES.filter((b) => b.parent === name).map((b) => outlinerNode(b.name)),
  ];
  return { uuid: g.uuid, isOpen: true, children };
};
const outliner = BONES.filter((b) => b.parent === null).map((b) => outlinerNode(b.name));

// transparent stub, replaced by generate_texture.mjs
const stub =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "parrot",
  model_identifier: "",
  visible_box: [1, 1, 0],
  variable_placeholders: "",
  resolution: { width: ATLAS_W, height: ATLAS_H },
  elements,
  groups,
  outliner,
  textures: [
    {
      name: "parrot",
      path: "",
      folder: "",
      namespace: "",
      id: "0",
      group: "",
      scope: 0,
      width: ATLAS_W,
      height: ATLAS_H,
      uv_width: ATLAS_W,
      uv_height: ATLAS_H,
      particle: false,
      use_as_default: false,
      layers_enabled: false,
      sync_to_project: "",
      file_format: "png",
      render_mode: "default",
      render_sides: "auto",
      wrap_mode: "limited",
      pbr_channel: "color",
      fps: 7,
      frame_time: 1,
      frame_order_type: "loop",
      frame_order: "",
      frame_interpolate: false,
      visible: true,
      internal: true,
      saved: false,
      uuid: randomUUID(),
      source: stub,
    },
  ],
};

// no-overlap assertion
const rects = elements.flatMap((e) => Object.values(e.faces).map((f) => f.uv));
for (let i = 0; i < rects.length; i++)
  for (let j = i + 1; j < rects.length; j++) {
    const [a, b] = [rects[i], rects[j]];
    if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3])
      throw new Error(`UV overlap: ${a} vs ${b}`);
  }
for (const r of rects) if (r.some((v) => !Number.isInteger(v))) throw new Error(`non-integer UV ${r}`);

writeFileSync(join(HERE, "parrot.bbmodel"), JSON.stringify(model, null, 2));
console.log(`parrot.bbmodel written: atlas ${ATLAS_W}x${ATLAS_H}, ${elements.length} cubes, ${groups.length} bones`);
