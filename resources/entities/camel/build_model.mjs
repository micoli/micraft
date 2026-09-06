// Throwaway geometry builder for camel.bbmodel — packs explicit per-face UVs.
// Run: make dc CMD="node resources/entities/camel/build_model.mjs"
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";

const ATLAS_W = 128;

// name, from, to  (16px = 1 block, feet at y=0)
const defs = [
  ["body",      [-6, 16, -9],  [6, 26, 9]],
  ["hump",      [-4, 26, -6],  [4, 32, 4]],
  ["neck",      [-3, 24, -12], [3, 38, -8]],
  ["head",      [-3, 36, -16], [3, 42, -8]],
  ["muzzle",    [-2, 36, -18], [2, 40, -16]],
  ["earR",      [1, 42, -12],  [3, 45, -10]],
  ["earL",      [-3, 42, -12], [-1, 45, -10]],
  ["frontLegR", [3, 0, -8],    [7, 16, -4]],
  ["frontLegL", [-7, 0, -8],   [-3, 16, -4]],
  ["backLegR",  [3, 0, 4],     [7, 16, 8]],
  ["backLegL",  [-7, 0, 4],    [-3, 16, 8]],
  ["tail",      [-1, 20, 9],   [1, 26, 12]],
];

// packer cursor
let cx = 0, cy = 0, rowH = 0;
function place(w, h) {
  if (cx + w > ATLAS_W) { cx = 0; cy += rowH; rowH = 0; }
  const r = [cx, cy, cx + w, cy + h];
  cx += w;
  rowH = Math.max(rowH, h);
  return r;
}

const elements = [];
const elUuid = {};
for (const [name, from, to] of defs) {
  const sx = to[0] - from[0], sy = to[1] - from[1], sz = to[2] - from[2];
  // face order fixed: north, east, south, west, up, down
  const faces = {
    north: { uv: place(sx, sy), texture: 0 },
    east:  { uv: place(sz, sy), texture: 0 },
    south: { uv: place(sx, sy), texture: 0 },
    west:  { uv: place(sz, sy), texture: 0 },
    up:    { uv: place(sx, sz), texture: 0 },
    down:  { uv: place(sx, sz), texture: 0 },
  };
  const uuid = randomUUID();
  elUuid[name] = uuid;
  elements.push({
    name, box_uv: false, render_order: "default", locked: false, export: true,
    scope: 0, allow_mirror_modeling: false, from, to, autouv: 0, color: 0,
    origin: [0, 0, 0], faces, type: "cube", uuid,
  });
}

const atlasH = cy + rowH;

// verify no overlap
function overlap(a, b) {
  return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
}
const rects = [];
for (const el of elements) for (const f of Object.values(el.faces)) rects.push([el.name, f.uv]);
for (let i = 0; i < rects.length; i++)
  for (let j = i + 1; j < rects.length; j++)
    if (overlap(rects[i][1], rects[j][1]))
      throw new Error(`UV overlap ${rects[i][0]} vs ${rects[j][0]}`);
for (const [, uv] of rects)
  if (uv.some((v) => !Number.isInteger(v))) throw new Error(`non-integer uv ${uv}`);

// bones: name, origin (pivot), rotation, [child element names], [child bone names]
const boneDefs = [
  ["body",      [0, 16, 0],    [0, 0, 0],   ["body"],            ["hump", "neck"]],
  ["hump",      [0, 26, 0],    [0, 0, 0],   ["hump"],            []],
  ["neck",      [0, 24, -9],   [0, 0, 0],   ["neck"],            ["head"]],
  ["head",      [0, 36, -9],   [0, 0, 0],   ["head"],            ["muzzle", "earR", "earL"]],
  ["muzzle",    [0, 38, -16],  [0, 0, 0],   ["muzzle"],          []],
  ["earR",      [2, 42, -11],  [0, 0, 0],   ["earR"],            []],
  ["earL",      [-2, 42, -11], [0, 0, 0],   ["earL"],            []],
  ["frontLegR", [5, 16, -6],   [0, 0, 0],   ["frontLegR"],       []],
  ["frontLegL", [-5, 16, -6],  [0, 0, 0],   ["frontLegL"],       []],
  ["backLegR",  [5, 16, 6],    [0, 0, 0],   ["backLegR"],        []],
  ["backLegL",  [-5, 16, 6],   [0, 0, 0],   ["backLegL"],        []],
  ["tail",      [0, 26, 10],   [-40, 0, 0], ["tail"],            []],
];

const boneUuid = {};
for (const [name] of boneDefs) boneUuid[name] = randomUUID();

const groups = boneDefs.map(([name, origin, rotation]) => ({
  name, uuid: boneUuid[name], export: true, locked: false, scope: 0, selected: false,
  _static: { properties: {}, temp_data: {} }, origin, rotation, color: 0,
  children: [], reset: false, shade: true, mirror_uv: false, visibility: true,
  autouv: 0, isOpen: true, primary_selected: false,
}));

function node(boneName) {
  const [, , , els, kids] = boneDefs.find((b) => b[0] === boneName);
  return {
    uuid: boneUuid[boneName], isOpen: true,
    children: [...kids.map(node), ...els.map((e) => elUuid[e])],
  };
}

const rootBones = ["body", "neck", "frontLegR", "frontLegL", "backLegR", "backLegL", "tail"];
// neck/head are nested under body in boneDefs kids -> remove neck from roots to avoid dup
const roots = ["body", "frontLegR", "frontLegL", "backLegR", "backLegL", "tail"];
const outliner = roots.map(node);

const model = {
  meta: { format_version: "5.0", model_format: "bedrock", box_uv: false },
  name: "camel",
  resolution: { width: ATLAS_W, height: atlasH },
  elements,
  groups,
  outliner,
  textures: [{
    name: "camel", path: "", folder: "", namespace: "", id: "0", group: "",
    scope: 0, width: ATLAS_W, height: atlasH, uv_width: ATLAS_W, uv_height: atlasH,
    particle: false, use_as_default: false, layers_enabled: false, sync_to_project: "",
    file_format: "png", render_mode: "default", render_sides: "auto", wrap_mode: "limited",
    pbr_channel: "color", fps: 7, frame_time: 1, frame_order_type: "loop", frame_order: "",
    frame_interpolate: false, visible: true, internal: true, saved: false, uuid: randomUUID(),
    source: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  }],
};

writeFileSync(new URL("./camel.bbmodel", import.meta.url), JSON.stringify(model));
console.log(`camel.bbmodel written: atlas ${ATLAS_W}x${atlasH}, ${elements.length} elements, ${rects.length} faces, no overlap`);
