import type { Scene } from "@babylonjs/core";
import {
  buildMeshElement,
  buildTextureMaterials,
  fixBoxSideFaceUV,
  fixBoxTopBottomFaceUV,
  isMeshElement,
  resolveTextureDims,
} from "./bbmodelMesh";

// A weapon/tool bbmodel has exactly one element named "handle" — no bone mapping needed,
// it is always parented to the rightItem/leftItem pivot of the hand it's equipped in.
const HANDLE_ELEMENT_NAME = "handle";

const WEAPON_SCALE = 1 / 16;

type WeaponElement = BbModelElement | BbModelMeshElement;

// The handle's own cuboid is the reference every other element (and the yaml `rotate`) is
// positioned/pivoted around, so re-centering or reshaping a weapon in Blockbench only means
// moving its "handle" cube — nothing else in the model needs to change.
function elementCenter(el: WeaponElement): [number, number, number] {
  if (isMeshElement(el)) {
    const points = Object.values(el.vertices);
    if (points.length === 0) return [0, 0, 0];
    const min: [number, number, number] = [Infinity, Infinity, Infinity];
    const max: [number, number, number] = [-Infinity, -Infinity, -Infinity];
    for (const p of points) {
      for (let i = 0; i < 3; i++) {
        if (p[i] < min[i]) min[i] = p[i];
        if (p[i] > max[i]) max[i] = p[i];
      }
    }
    return [(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2];
  }
  const [fx, fy, fz] = el.from;
  const [tx, ty, tz] = el.to;
  return [(fx + tx) / 2, (fy + ty) / 2, (fz + tz) / 2];
}

function fetchHandItemModel(name: string): Promise<BbModel> {
  return fetch(`/api/models/weapons/${name}/${name}.bbmodel`).then((r) => {
    if (r.ok) return r.json();
    return fetch(`/api/models/tools/${name}/${name}.bbmodel`).then((r2) => {
      if (r2.ok) return r2.json();
      throw new Error(`Hand item model ${name} not found under weapons/ or tools/`);
    });
  });
}

// WeaponDefinition/ToolDefinition both carry a `rotate: {x,y,z}` (degrees) applied to the handle
// anchor when equipped — look it up from whichever registry defines this item.
function fetchHandItemRotate(name: string): Promise<{ x: number; y: number; z: number }> {
  const zero = { x: 0, y: 0, z: 0 };
  return fetch("/api/weapons")
    .then((r) => (r.ok ? r.json() : {}))
    .then((weapons: Record<string, { rotate?: { x: number; y: number; z: number } }>) => {
      if (weapons[name]) return weapons[name].rotate ?? zero;
      return fetch("/api/tools")
        .then((r) => (r.ok ? r.json() : {}))
        .then((tools: Record<string, { rotate?: { x: number; y: number; z: number } }>) => tools[name]?.rotate ?? zero);
    })
    .catch(() => zero);
}

export function registerWeaponOverlay(): Pick<
  McBindings,
  "initWeaponModel" | "isWeaponModelReady" | "attachWeapon" | "detachWeapon" | "detachAllWeapons"
> {
  return {
    initWeaponModel: (name: string): void => {
      if (!window.mcState.weaponRotations[name]) {
        fetchHandItemRotate(name).then((rotate) => {
          window.mcState.weaponRotations[name] = rotate;
        });
      }
      if (window.mcState.weaponBbmodels[name]) return;
      fetchHandItemModel(name)
        .then((data: BbModel) => {
          window.mcState.weaponBbmodels[name] = data;
          console.log(`[MiCraft] Hand item model ${name} loaded`);
        })
        .catch((e) => {
          console.error(`[MiCraft] Failed to load hand item model ${name}`, e);
        });
    },

    isWeaponModelReady: (name: string): boolean =>
      !!window.mcState?.weaponBbmodels?.[name] && !!window.mcState?.weaponRotations?.[name],

    attachWeapon: (model: McPlayerModel, itemName: string, scene: Scene, hand: "LEFT" | "RIGHT"): void => {
      if (model.equippedWeapons[hand]) return; // already attached

      const bbmodel = window.mcState?.weaponBbmodels?.[itemName];
      if (!bbmodel) return;

      const elements = bbmodel.elements as unknown as WeaponElement[];
      const handleEl = elements.find((el) => el.name === HANDLE_ELEMENT_NAME);
      if (!handleEl) {
        console.error(`[MiCraft] Hand item model ${itemName} has no "${HANDLE_ELEMENT_NAME}" element`);
        return;
      }

      const pivotKey = hand === "LEFT" ? "leftItem" : "rightItem";
      const pg = model.pivotNodes[pivotKey];
      if (!pg) return;

      const W = bbmodel.resolution.width;
      const H = bbmodel.resolution.height;
      if (!scene.__mcSceneId) scene.__mcSceneId = Math.random().toString(36).slice(2);
      const cacheKey = `weapon_${itemName}_${scene.__mcSceneId}`;
      const materials = buildTextureMaterials(bbmodel, scene, cacheKey, true);
      const mat = materials[0] ?? null;
      const textureDims = resolveTextureDims(bbmodel);

      // Anchor: sits at the hand's barycenter, rotated per the item's `rotate` yaml property
      // (degrees, see WeaponDefinition/ToolDefinition). Every element of the bbmodel — including
      // "handle" itself — is rendered relative to the handle's own cuboid center, so the whole
      // weapon/tool shape follows the anchor, and the yaml `rotate` pivots around the handle.
      // Repositioning or reshaping a model in Blockbench is then just a matter of moving the
      // "handle" cube — no other element needs touching.
      const anchor = new BABYLON.TransformNode(`weapon_${itemName}_${hand}_anchor`, scene);
      anchor.parent = pg.node;
      anchor.position = BABYLON.Vector3.Zero();
      const rotate = window.mcState.weaponRotations[itemName];
      const DEG = Math.PI / 180;
      // kotlinx.serialization omits default (0) properties from the JSON payload, so any axis can
      // be `undefined` here — coalesce each one individually rather than the whole object.
      anchor.rotation = new BABYLON.Vector3((rotate?.x ?? 0) * DEG, (rotate?.y ?? 0) * DEG, (rotate?.z ?? 0) * DEG);

      const [hcx, hcy, hcz] = elementCenter(handleEl);

      for (const el of elements) {
        if (el.visibility === false) continue;

        if (isMeshElement(el)) {
          if (Object.keys(el.vertices).length === 0) continue;
          const mesh = buildMeshElement(
            `weapon_${itemName}_${el.name}`,
            el,
            scene,
            [hcx, hcy, hcz],
            materials,
            textureDims,
          );
          mesh.parent = anchor;
          continue;
        }

        const [fx, fy, fz] = el.from;
        const [tx, ty, tz] = el.to;
        if (Math.abs(tx - fx) < 0.001 || Math.abs(ty - fy) < 0.001 || Math.abs(tz - fz) < 0.001) continue;

        const faceUVs = window.mcState.skinFaceUV(el, W, H);
        const mesh = BABYLON.MeshBuilder.CreateBox(
          `weapon_${itemName}_${el.name}`,
          {
            width: Math.abs(tx - fx) * WEAPON_SCALE,
            height: Math.abs(ty - fy) * WEAPON_SCALE,
            depth: Math.abs(tz - fz) * WEAPON_SCALE,
            faceUV: faceUVs,
          },
          scene,
        );
        fixBoxSideFaceUV(mesh, faceUVs[2], faceUVs[3]);
        fixBoxTopBottomFaceUV(mesh, faceUVs[4], faceUVs[5]);
        mesh.material = mat;
        mesh.isPickable = false;
        mesh.parent = anchor;

        const cx = (fx + tx) / 2;
        const cy = (fy + ty) / 2;
        const cz = (fz + tz) / 2;
        mesh.position = new BABYLON.Vector3(
          (cx - hcx) * WEAPON_SCALE,
          (cy - hcy) * WEAPON_SCALE,
          (cz - hcz) * WEAPON_SCALE,
        );
      }

      model.equippedWeapons[hand] = anchor;
    },

    detachWeapon: (model: McPlayerModel, hand: "LEFT" | "RIGHT"): void => {
      const anchor = model.equippedWeapons[hand];
      if (!anchor) return;
      anchor.dispose();
      model.equippedWeapons[hand] = null;
    },

    detachAllWeapons: (model: McPlayerModel): void => {
      (["LEFT", "RIGHT"] as const).forEach((hand) => {
        model.equippedWeapons[hand]?.dispose();
        model.equippedWeapons[hand] = null;
      });
    },
  };
}
