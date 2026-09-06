// Generates resources/entities/cat_baby/cat_baby.bbmodel — kitten variant of the cat.
// Shares all geometry/UV/texture logic with ../cat/generate_texture.mjs; only the
// proportions (bigger head, stubby legs, shorter tail) and seed differ.
// Run: make dc CMD="node resources/entities/cat_baby/generate_texture.mjs"
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));

globalThis.__CAT_CFG__ = {
  type: "cat_baby",
  seed: 0x5eed_ba9,
  file: join(HERE, "cat_baby.bbmodel"),
  tailRot: -50,
  elements: [
    { name: "body", from: [-2.5, 4, -4], to: [2.5, 8, 4], bone: "body", region: "body" },
    { name: "head", from: [-2.5, 6, -8], to: [2.5, 11, -4], bone: "head", region: "head" },
    { name: "earL", from: [1, 10.5, -7], to: [3, 13, -6], bone: "head", region: "ear" },
    { name: "earR", from: [-3, 10.5, -7], to: [-1, 13, -6], bone: "head", region: "ear" },
    { name: "tail", from: [-1, 6, 3], to: [1, 12, 5], bone: "tail", region: "tail" },
    { name: "frontLegL", from: [-2.5, 0, -3.5], to: [-0.5, 4, -1.5], bone: "frontLegL", region: "leg" },
    { name: "frontLegR", from: [0.5, 0, -3.5], to: [2.5, 4, -1.5], bone: "frontLegR", region: "leg" },
    { name: "backLegL", from: [-2.5, 0, 1.5], to: [-0.5, 4, 3.5], bone: "backLegL", region: "leg" },
    { name: "backLegR", from: [0.5, 0, 1.5], to: [2.5, 4, 3.5], bone: "backLegR", region: "leg" },
  ],
  bones: [
    { name: "body", origin: [0, 5, 0], rotation: [0, 0, 0], root: true },
    { name: "head", origin: [0, 7, -4], rotation: [0, 0, 0], parent: "body" },
    { name: "tail", origin: [0, 7, 3], rotation: [-50, 0, 0], parent: "body" },
    { name: "frontLegL", origin: [-1.5, 4, -2.5], rotation: [0, 0, 0], parent: "body" },
    { name: "frontLegR", origin: [1.5, 4, -2.5], rotation: [0, 0, 0], parent: "body" },
    { name: "backLegL", origin: [-1.5, 4, 2.5], rotation: [0, 0, 0], parent: "body" },
    { name: "backLegR", origin: [1.5, 4, 2.5], rotation: [0, 0, 0], parent: "body" },
  ],
};

await import("../cat/generate_texture.mjs");
