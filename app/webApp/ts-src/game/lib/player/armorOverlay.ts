import type { Scene } from "@babylonjs/core";
import {
  buildMeshElement,
  buildTextureMaterials,
  fixBoxSideFaceUV,
  fixBoxTopBottomFaceUV,
  isMeshElement,
  resolveTextureDims,
} from "./bbmodelMesh";

type ArmorElement = BbModelElement | BbModelMeshElement;

// Fallback alias table for armor bbmodels whose group names don't match the body
// rig 1:1 (e.g. a single unsplit arm/leg mesh, or a differently-named legacy rig).
// Armor bbmodels exported from the same rig as articulated.bbmodel share bone names
// directly with model.pivotNodes and never consult this table.
const ARMOR_TO_PIVOT: Record<string, string | null> = {
  // Whole-limb bones — kept for armor bbmodels with a single unsplit arm/leg mesh.
  rightarm: "rightArm",
  leftarm: "leftArm",
  rightleg: "rightLeg",
  leftleg: "leftLeg",
  // Segmented limb slots, mapped onto the same pivot bones as their whole-limb counterparts.
  rightbiceps: "rightArm",
  rightforearm: "rightElbow",
  righthand: "rightWrist",
  leftbiceps: "leftArm",
  leftforearm: "leftElbow",
  lefthand: "leftWrist",
  rightthigh: "rightLeg",
  rightcalf: "rightKnee",
  rightfoot: "rightAnkle",
  leftthigh: "leftLeg",
  leftcalf: "leftKnee",
  leftfoot: "leftAnkle",
};

function normalizeBoneName(name: string): string {
  return name.toLowerCase().replace(/\s+/g, "");
}

export function registerArmorOverlay(): Pick<
  McBindings,
  "initArmorModel" | "isArmorModelReady" | "attachArmor" | "detachArmor" | "detachAllArmors"
> {
  return {
    initArmorModel: (name: string): void => {
      if (window.mcState.armorBbmodels[name]) return;
      // /api/models is a staticFiles mount (Application.kt), not an OpenAPI route.
      fetch(`/api/models/armors/${name}/${name}.bbmodel`)
        .then((r) => r.json())
        .then((data: BbModel) => {
          window.mcState.armorBbmodels[name] = data;
          console.log(`[MiCraft] Armor model ${name} loaded`);
        })
        .catch((e) => {
          console.error(`[MiCraft] Failed to load armor model ${name}`, e);
        });
    },

    isArmorModelReady: (name: string): boolean => !!window.mcState?.armorBbmodels?.[name],

    attachArmor: (model: McPlayerModel, armorName: string, scene: Scene): void => {
      if (model.equippedArmors[armorName]) return; // already attached

      const bbmodel = window.mcState?.armorBbmodels?.[armorName];
      if (!bbmodel) return;

      const W = bbmodel.resolution.width;
      const H = bbmodel.resolution.height;
      const SCALE = 1 / 16;

      // Materials — one per armor texture, cached per scene
      if (!scene.__mcSceneId) scene.__mcSceneId = Math.random().toString(36).slice(2);
      const cacheKey = `armor_${armorName}_${scene.__mcSceneId}`;
      const materials = buildTextureMaterials(bbmodel, scene, cacheKey, true);
      const mat = materials[0] ?? null;
      const textureDims = resolveTextureDims(bbmodel);

      // Map element uuid → pivot key via outliner
      const groupMap: Record<string, BbModelGroup> = {};
      bbmodel.groups.forEach((g) => {
        groupMap[g.uuid] = g;
      });

      const elToGroup: Record<string, string | null | undefined> = {};
      const hiddenEls = new Set<string>();
      function walkOutliner(
        nodes: BbModel["outliner"],
        pivotKey: string | null | undefined,
        groupHidden: boolean,
      ): void {
        if (!nodes) return;
        for (const node of nodes) {
          if (typeof node === "string") {
            elToGroup[node] = pivotKey;
            if (groupHidden) hiddenEls.add(node);
            continue;
          }
          const objectNode = node as { uuid: string; children?: BbModel["outliner"] };
          const g = groupMap[objectNode.uuid];
          const normalized = g ? normalizeBoneName(g.name) : null;
          let next = pivotKey;
          if (g && g.name in model.pivotNodes) {
            next = g.name; // rig shares bone names with the body model — use directly
          } else if (normalized && normalized in ARMOR_TO_PIVOT) {
            next = ARMOR_TO_PIVOT[normalized];
          }
          const isHidden = groupHidden || g?.visibility === false;
          walkOutliner(objectNode.children ?? [], next, isHidden);
        }
      }
      walkOutliner(bbmodel.outliner, undefined, false);

      const meshes: InstanceType<typeof BABYLON.AbstractMesh>[] = [];

      for (const el of bbmodel.elements as unknown as ArmorElement[]) {
        if (hiddenEls.has(el.uuid) || el.visibility === false) continue;

        const pivotKey = elToGroup[el.uuid]; // string | null | undefined
        if (pivotKey === undefined) continue; // bone not mapped → skip
        const pg = pivotKey !== null ? model.pivotNodes[pivotKey] : null;
        if (pivotKey !== null && !pg) continue;

        if (isMeshElement(el)) {
          if (Object.keys(el.vertices).length === 0) continue;
          // Mesh geometry is baked relative to `center` (bbmodel pixel space) — the bone pivot's
          // own origin when parented to a limb, or the model origin when parented to root/body.
          const center: [number, number, number] = pg ? pg.origin : [0, 0, 0];
          const mesh = buildMeshElement(`armor_${armorName}_${el.name}`, el, scene, center, materials, textureDims);
          mesh.isPickable = false;
          mesh.parent = pg ? pg.node : model.root;
          meshes.push(mesh);
          continue;
        }

        const [fx, fy, fz] = el.from;
        const [tx, ty, tz] = el.to;
        if (Math.abs(tx - fx) < 0.001 || Math.abs(ty - fy) < 0.001 || Math.abs(tz - fz) < 0.001) continue;

        const faceUVs = window.mcState.skinFaceUV(el, W, H);
        const mesh = BABYLON.MeshBuilder.CreateBox(
          `armor_${armorName}_${el.name}`,
          {
            width: Math.abs(tx - fx) * SCALE,
            height: Math.abs(ty - fy) * SCALE,
            depth: Math.abs(tz - fz) * SCALE,
            faceUV: faceUVs,
          },
          scene,
        );
        fixBoxSideFaceUV(mesh, faceUVs[2], faceUVs[3]);
        fixBoxTopBottomFaceUV(mesh, faceUVs[4], faceUVs[5]);
        mesh.material = mat;
        mesh.isPickable = false;

        const cx = ((fx + tx) / 2) * SCALE;
        const cy = ((fy + ty) / 2) * SCALE;
        const cz = ((fz + tz) / 2) * SCALE;

        if (pg) {
          mesh.parent = pg.node;
          mesh.position = new BABYLON.Vector3(
            cx - pg.origin[0] * SCALE,
            cy - pg.origin[1] * SCALE,
            cz - pg.origin[2] * SCALE,
          );
        } else {
          // body — static, parent to root
          mesh.parent = model.root;
          mesh.position = new BABYLON.Vector3(cx, cy, cz);
        }

        meshes.push(mesh);
      }

      model.equippedArmors[armorName] = meshes;
    },

    detachArmor: (model: McPlayerModel, armorName: string): void => {
      const meshes = model.equippedArmors[armorName];
      if (!meshes) return;
      meshes.forEach((m) => m.dispose());
      delete model.equippedArmors[armorName];
    },

    detachAllArmors: (model: McPlayerModel): void => {
      for (const name of Object.keys(model.equippedArmors)) {
        model.equippedArmors[name]?.forEach((m) => m.dispose());
      }
      model.equippedArmors = {};
    },
  };
}
