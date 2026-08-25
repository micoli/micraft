import type { Scene } from "@babylonjs/core";
import {
  buildGroupHierarchy,
  buildTextureMaterials,
  placeElements,
  resolveTextureDims,
  skinFaceUV,
  skinUV,
} from "./bbmodelMesh";

// Linear interpolation between two bbmodel keyframes. `t` is clip-relative time in SECONDS, on the
// same scale as `keyframe.time` and the animation `length` — not a normalised 0..1 phase.
export function interpAxis(keyframes: BbModelKeyframe[], t: number, axis: string): number {
  if (!keyframes || keyframes.length === 0) return 0;
  if (keyframes.length === 1) return parseFloat(String(keyframes[0].data_points[0][axis] ?? 0));
  let prev = keyframes[0],
    next = keyframes[keyframes.length - 1];
  for (let i = 0; i < keyframes.length - 1; i++) {
    if (t >= keyframes[i].time && t <= keyframes[i + 1].time) {
      prev = keyframes[i];
      next = keyframes[i + 1];
      break;
    }
  }
  if (prev === next) return parseFloat(String(prev.data_points[0][axis] ?? 0));
  const span = next.time - prev.time;
  const f = span <= 0 ? 0 : (t - prev.time) / span;
  const v0 = parseFloat(String(prev.data_points[0][axis] ?? 0));
  const v1 = parseFloat(String(next.data_points[0][axis] ?? 0));
  return v0 + (v1 - v0) * f;
}

const ANIM_GROUPS = [
  "head",
  "rightArm",
  "leftArm",
  "rightLeg",
  "leftLeg",
  // Articulation pivots present on the articulated rig only (absent on plain 8-bone rigs —
  // extractNamedAnim/setPlayerTransform skip any bone missing from a given model).
  "rightElbow",
  "rightWrist",
  "leftElbow",
  "leftWrist",
  "rightKnee",
  "rightAnkle",
  "leftKnee",
  "leftAnkle",
  // Torso/hip counter-sway (rotation.z, not .x — see TORSO_BONES below). "root" and "waist" also
  // appear in every clip but are always zero (unused rig leftovers) — not worth extracting.
  "pelvis",
  "body",
] as const;

// Bones driven by rotation.x in setPlayerTransform — head is handled separately (also tracks pitch).
const LIMB_BONES = [
  "rightArm",
  "leftArm",
  "rightLeg",
  "leftLeg",
  "rightElbow",
  "rightWrist",
  "leftElbow",
  "leftWrist",
  "rightKnee",
  "rightAnkle",
  "leftKnee",
  "leftAnkle",
] as const;

// Torso/hip bones driven by rotation.z (side-to-side counter-sway), not rotation.x.
const TORSO_BONES = ["pelvis", "body"] as const;

// Maps each non-idle clip to the suffix of its bbmodel animator name
// (`animation.default_player.<Name>`). "idle" has no clip — it's the rest pose.
const CLIP_NAME_MAP: Record<Exclude<PlayerAnimClip, "idle">, string> = {
  walking_forward: "walking_a",
  walking_backward: "walking_backwards",
  sneaking: "sneaking",
  crawling: "crawling",
  jump_idle: "jump_idle",
  strafe_left: "running_strafe_left",
  strafe_right: "running_strafe_right",
  sitting: "sit_chair_idle",
};

function extractNamedAnim(
  bbmodel: BbModel,
  bones: readonly string[],
  clipSuffix: string,
): Record<string, { keyframes: BbModelKeyframe[]; length: number }> | undefined {
  const animDef = bbmodel.animations?.find((a) => a.name?.toLowerCase().endsWith(clipSuffix));
  if (!animDef) return undefined;

  const nameToUuid: Record<string, string> = {};
  bbmodel.groups.forEach((g) => {
    nameToUuid[g.name] = g.uuid;
  });

  const result: Record<string, { keyframes: BbModelKeyframe[]; length: number }> = {};
  for (const bname of bones) {
    const uuid = nameToUuid[bname];
    if (!uuid) continue;
    const animator = animDef.animators[uuid];
    if (!animator) continue;
    const kfs = animator.keyframes.filter((k) => k.channel === "rotation").sort((a, b) => a.time - b.time);
    if (kfs.length > 0) result[bname] = { keyframes: kfs, length: animDef.length || 1 };
  }
  return Object.keys(result).length > 0 ? result : undefined;
}

function extractPlayerAnimations(
  bbmodel: BbModel,
  bones: readonly string[],
): Partial<Record<PlayerAnimClip, Record<string, { keyframes: BbModelKeyframe[]; length: number }>>> {
  const result: Partial<Record<PlayerAnimClip, Record<string, { keyframes: BbModelKeyframe[]; length: number }>>> = {};
  for (const [clip, suffix] of Object.entries(CLIP_NAME_MAP) as [Exclude<PlayerAnimClip, "idle">, string][]) {
    const anim = extractNamedAnim(bbmodel, bones, suffix);
    if (anim) result[clip] = anim;
  }
  return result;
}

export function registerPlayerModel(): Pick<
  McBindings,
  | "initPlayerModel"
  | "isPlayerBbmodelReady"
  | "createPlayerModelNow"
  | "createPlayerModelFromBbmodel"
  | "setPlayerTransform"
  | "setPlayerVisible"
  | "setPlayerAlpha"
  | "setPlayerFirstPerson"
  | "disposePlayerModel"
> {
  window.mcState.skinUV = skinUV;
  window.mcState.skinFaceUV = skinFaceUV;

  function createPlayerModelFromBbmodel(
    bbmodel: BbModel,
    scene: Scene,
    skin: string = "articulated",
    boneAliases?: Record<string, string>,
  ): McPlayerModel {
    if (!scene.__mcSceneId) scene.__mcSceneId = Math.random().toString(36).slice(2);
    const cacheKey = `${scene.__mcSceneId}_${skin}`;
    const materials = buildTextureMaterials(bbmodel, scene, cacheKey, false);
    const textureDims = resolveTextureDims(bbmodel);
    const root = new BABYLON.TransformNode("playerRoot", scene);
    const { pivotNodes, allGroupNodes, elToGroupUuid } = buildGroupHierarchy(bbmodel, scene, root, boneAliases);
    placeElements(bbmodel, scene, { elToGroupUuid, allGroupNodes }, root, materials, textureDims);

    return {
      root,
      headNode: pivotNodes["head"]?.node ?? null,
      pivotNodes,
      animations: extractPlayerAnimations(bbmodel, [...ANIM_GROUPS, "head"]),
      equippedArmors: {},
      equippedWeapons: { LEFT: null, RIGHT: null },
    };
  }

  return {
    initPlayerModel: (skin: string): void => {
      if (window.mcState.playerBbmodels[skin]) return;
      // /api/models is a staticFiles mount (Application.kt), not an OpenAPI route.
      fetch(`/api/models/models/${skin}/${skin}.bbmodel`)
        .then((r) => r.json())
        .then((data: BbModel) => {
          window.mcState.playerBbmodels[skin] = data;
          console.log(`[MiCraft] Player model ${skin} loaded`);
        })
        .catch((e) => {
          console.error(`[MiCraft] Failed to load player model ${skin}`, e);
        });
    },

    isPlayerBbmodelReady: (skin: string): boolean => !!window.mcState?.playerBbmodels?.[skin],

    createPlayerModelNow: (scene: Scene, skin: string): McPlayerModel =>
      createPlayerModelFromBbmodel(window.mcState.playerBbmodels[skin]!, scene, skin),

    createPlayerModelFromBbmodel,

    setPlayerTransform: (
      model: McPlayerModel,
      x: number,
      y: number,
      z: number,
      yaw: number,
      headPitch: number,
      clip: PlayerAnimClip,
    ): void => {
      model.root.position.x = x;
      model.root.position.y = y;
      model.root.position.z = z;
      model.root.rotation.y = yaw + Math.PI;

      const pn = model.pivotNodes;
      if (!pn) return;
      const DEG = Math.PI / 180;
      const headPivot = pn["head"]?.node ?? null;
      const anim = clip !== "idle" ? model.animations?.[clip] : undefined;

      if (anim) {
        const animLen = Math.max(anim["rightArm"]?.length ?? 1, 1e-3);
        const tSec = (Date.now() % (animLen * 1000)) / 1000;
        for (const bname of LIMB_BONES) {
          if (!pn[bname]) continue;
          pn[bname].node.rotation.x = (anim[bname] ? interpAxis(anim[bname].keyframes, tSec, "x") : 0) * DEG;
        }
        for (const bname of TORSO_BONES) {
          if (!pn[bname]) continue;
          pn[bname].node.rotation.z = (anim[bname] ? interpAxis(anim[bname].keyframes, tSec, "z") : 0) * DEG;
        }
        if (headPivot) {
          const hb = anim["head"];
          headPivot.rotation.x = -headPitch + (hb ? interpAxis(hb.keyframes, tSec, "x") : 0) * DEG;
          headPivot.rotation.y = (hb ? interpAxis(hb.keyframes, tSec, "y") : 0) * DEG;
        }
      } else {
        for (const bname of LIMB_BONES) {
          if (pn[bname]) pn[bname].node.rotation.x = 0;
        }
        for (const bname of TORSO_BONES) {
          if (pn[bname]) pn[bname].node.rotation.z = 0;
        }
        if (headPivot) {
          headPivot.rotation.x = -headPitch;
          headPivot.rotation.y = 0;
        }
      }
    },

    setPlayerVisible: (model: McPlayerModel, visible: boolean): void => {
      model.root.setEnabled(visible);
    },

    setPlayerAlpha: (model: McPlayerModel, alpha: number): void => {
      model.root.getChildMeshes(true).forEach((m) => {
        m.visibility = alpha;
      });
    },

    // First person shows the player's own body, minus the bones listed in the skin configEditor
    // (head + helmet), since the camera sits inside the head. Re-applied every frame so
    // armor pieces attached later are covered too.
    setPlayerFirstPerson: (model: McPlayerModel, skin: string, enabled: boolean): void => {
      const hidden = enabled ? (window.mcState.skinConfigs[skin]?.firstPersonHiddenBones ?? []) : [];
      model.root.getChildMeshes(false).forEach((m) => {
        m.isVisible = true;
      });
      for (const bone of hidden) {
        model.pivotNodes[bone]?.node.getChildMeshes(false).forEach((m) => {
          m.isVisible = false;
        });
      }
    },

    disposePlayerModel: (model: McPlayerModel): void => {
      model.root.getChildMeshes(true).forEach((m) => m.dispose());
      Object.values(model.pivotNodes).forEach((p) => p.node.dispose());
      model.root.dispose();
    },
  };
}
