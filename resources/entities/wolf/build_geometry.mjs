// Authors resources/entities/wolf/wolf.bbmodel — geometry + packed per-face UV atlas.
// Per add-npc-model skill: box_uv:false everywhere, every face gets its own disjoint rect
// computed by a deterministic row packer. Texture pixels are painted by generate_texture.mjs.
// Run: make dc CMD="node resources/entities/wolf/build_geometry.mjs"
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 64;
const OUT = new URL("./wolf.bbmodel", import.meta.url);

// name -> [from, to] in pixels (16px = 1 block, feet at y=0, -z = front)
const CUBES = [
  ["body", [-3, 9, -5], [3, 15, 7]],
  ["head", [-3, 12, -11], [3, 18, -5]],
  ["snout", [-2, 12, -14], [2, 15, -11]],
  ["earL", [-3, 18, -9], [-1, 21, -7]],
  ["earR", [1, 18, -9], [3, 21, -7]],
  ["tail", [-1.5, 8, 7], [1.5, 14, 11]],
  ["rightArm", [1, 0, -4], [3, 9, -2]],
  ["leftArm", [-3, 0, -4], [-1, 9, -2]],
  ["rightLeg", [1, 0, 3], [3, 9, 5]],
  ["leftLeg", [-3, 0, 3], [-1, 9, 5]],
];

// bone -> { origin, rotation, cubes:[names] }
const BONES = {
  body: { origin: [0, 12, 0], rotation: [0, 0, 0], cubes: ["body"] },
  head: { origin: [0, 13, -5], rotation: [0, 0, 0], cubes: ["head", "snout", "earL", "earR"] },
  tail: { origin: [0, 14, 7], rotation: [-45, 0, 0], cubes: ["tail"] },
  rightArm: { origin: [2, 9, -3], rotation: [0, 0, 0], cubes: ["rightArm"] },
  leftArm: { origin: [-2, 9, -3], rotation: [0, 0, 0], cubes: ["leftArm"] },
  rightLeg: { origin: [2, 9, 4], rotation: [0, 0, 0], cubes: ["rightLeg"] },
  leftLeg: { origin: [-2, 9, 4], rotation: [0, 0, 0], cubes: ["leftLeg"] },
};
const BONE_ORDER = ["body", "head", "tail", "rightArm", "leftArm", "rightLeg", "leftLeg"];

const FACE_ORDER = ["north", "east", "south", "west", "up", "down"];
function faceSize(dx, dy, dz, face) {
  if (face === "north" || face === "south") return [dx, dy];
  if (face === "east" || face === "west") return [dz, dy];
  return [dx, dz]; // up / down
}

// deterministic row packer -> every rect disjoint by construction
let cx = 0, cy = 0, rowH = 0, maxY = 0;
function pack(w, h) {
  if (cx + w > ATLAS_W) { cx = 0; cy += rowH; rowH = 0; }
  const r = [cx, cy, cx + w, cy + h];
  cx += w; rowH = Math.max(rowH, h); maxY = Math.max(maxY, cy + h);
  return r;
}

const elements = CUBES.map(([name, from, to]) => {
  const [dx, dy, dz] = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
  if (![dx, dy, dz].every((v) => Number.isInteger(v))) throw new Error(`${name}: non-integer size`);
  const faces = {};
  for (const f of FACE_ORDER) {
    const [w, h] = faceSize(dx, dy, dz, f);
    faces[f] = { uv: pack(w, h), texture: 0 };
  }
  return {
    name, box_uv: false, render_order: "default", locked: false, export: true, scope: 0,
    allow_mirror_modeling: false, from, to, autouv: 0, color: 0, origin: [0, 0, 0],
    faces, type: "cube", uuid: randomUUID(),
  };
});
const elByName = Object.fromEntries(elements.map((e) => [e.name, e]));

const groups = BONE_ORDER.map((bn) => {
  const b = BONES[bn];
  return {
    name: bn, uuid: randomUUID(), export: true, locked: false, scope: 0, selected: false,
    _static: { properties: {}, temp_data: {} }, origin: b.origin, rotation: b.rotation, color: 0,
    children: [], reset: false, shade: true, mirror_uv: false, visibility: true, autouv: 0,
    isOpen: true, primary_selected: false,
  };
});
const grpByName = Object.fromEntries(groups.map((g) => [g.name, g]));

const outliner = BONE_ORDER.map((bn) => ({
  uuid: grpByName[bn].uuid,
  isOpen: true,
  children: BONES[bn].cubes.map((cn) => elByName[cn].uuid),
}));

// overlap sanity check (skill step 2)
const rects = elements.flatMap((e) => Object.values(e.faces).map((f) => [e.name, f.uv]));
for (let i = 0; i < rects.length; i++) {
  for (let j = i + 1; j < rects.length; j++) {
    const [an, a] = rects[i], [bn, b] = rects[j];
    if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]) {
      throw new Error(`UV overlap: ${an} ${a} vs ${bn} ${b}`);
    }
    if (![...a, ...b].every(Number.isInteger)) throw new Error("non-integer uv");
  }
}

const height = maxY;
const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "wolf",
  model_identifier: "",
  visible_box: [1, 1, 0],
  variable_placeholders: "",
  resolution: { width: ATLAS_W, height },
  elements,
  groups,
  outliner,
  textures: [{
    name: "wolf", path: "", folder: "", namespace: "", id: "0", group: "",
    scope: 0, width: ATLAS_W, height, uv_width: ATLAS_W, uv_height: height,
    particle: false, use_as_default: false, layers_enabled: false, sync_to_project: "",
    file_format: "png", render_mode: "default", render_sides: "auto", wrap_mode: "limited",
    pbr_channel: "color", fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "",
    frame_interpolate: false, visible: true, internal: true, saved: false,
    uuid: randomUUID(), source: "",
  }],
};

writeFileSync(OUT, JSON.stringify(model));
console.log(`wrote wolf.bbmodel  atlas ${ATLAS_W}x${height}  ${elements.length} cubes  ${groups.length} bones`);
