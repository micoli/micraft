import type { Scene } from "@babylonjs/core";

// Normalize armor bone name to player pivot key.
// "Right Arm" → "rightarm" → "rightArm"
const ARMOR_TO_PIVOT: Record<string, string | null> = {
  head: "head",
  body: null, // no animated pivot — parent to model.root
  rightarm: "rightArm",
  leftarm: "leftArm",
  rightleg: "rightLeg",
  leftleg: "leftLeg",
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

      // Material — one per armor type, cached per scene
      const s = scene as any;
      if (!s.__mcSceneId) s.__mcSceneId = Math.random().toString(36).slice(2);
      const cacheKey = `armor_${armorName}_${s.__mcSceneId}`;
      if (bbmodel.textures?.length > 0 && !window.mcState.skinMatCache[cacheKey]) {
        const texDef = bbmodel.textures[0];
        const tex = new BABYLON.Texture(texDef.source, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
        tex.hasAlpha = true;
        tex.wrapU = BABYLON.Texture.CLAMP_ADDRESSMODE;
        tex.wrapV = BABYLON.Texture.CLAMP_ADDRESSMODE;
        const mat = new BABYLON.StandardMaterial(`armorMat_${armorName}`, scene);
        mat.diffuseTexture = tex;
        mat.specularColor = new BABYLON.Color3(0, 0, 0);
        mat.useAlphaFromDiffuseTexture = true;
        window.mcState.skinMatCache[cacheKey] = mat;
      }
      const mat = window.mcState.skinMatCache[cacheKey];

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
          const g = groupMap[(node as any).uuid];
          const normalized = g ? normalizeBoneName(g.name) : null;
          const next = normalized && normalized in ARMOR_TO_PIVOT ? ARMOR_TO_PIVOT[normalized] : pivotKey;
          const isHidden = groupHidden || g?.visibility === false;
          walkOutliner((node as any).children, next, isHidden);
        }
      }
      walkOutliner(bbmodel.outliner, undefined, false);

      const meshes: InstanceType<typeof BABYLON.AbstractMesh>[] = [];

      for (const el of bbmodel.elements) {
        if (hiddenEls.has(el.uuid) || el.visibility === false) continue;
        const [fx, fy, fz] = el.from;
        const [tx, ty, tz] = el.to;
        if (Math.abs(tx - fx) < 0.001 || Math.abs(ty - fy) < 0.001 || Math.abs(tz - fz) < 0.001) continue;

        const mesh = BABYLON.MeshBuilder.CreateBox(
          `armor_${armorName}_${el.name}`,
          {
            width: Math.abs(tx - fx) * SCALE,
            height: Math.abs(ty - fy) * SCALE,
            depth: Math.abs(tz - fz) * SCALE,
            faceUV: window.mcState.skinFaceUV(el.faces, W, H) as any,
          },
          scene,
        );
        mesh.material = mat;
        mesh.isPickable = false;

        const cx = ((fx + tx) / 2) * SCALE;
        const cy = ((fy + ty) / 2) * SCALE;
        const cz = ((fz + tz) / 2) * SCALE;

        const pivotKey = elToGroup[el.uuid]; // string | null | undefined
        if (pivotKey === undefined) {
          // bone not mapped → skip
          mesh.dispose();
          continue;
        }

        if (pivotKey !== null) {
          const pg = model.pivotNodes[pivotKey];
          if (!pg) {
            mesh.dispose();
            continue;
          }
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
