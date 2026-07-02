import type { Camera, Scene, StandardMaterial } from "@babylonjs/core";
import { interpAxis } from "./playerModel";

export function registerFPArms(): Pick<
  McBindings,
  "createFPArms" | "updateFPArms" | "setFPArmsVisible" | "disposeFPArms" | "debugFPArms"
> {
  return {
    createFPArms: (scene: Scene, camera: Camera, skin: string = "player"): McFPArms | null => {
      const bbmodel = window.mcState?.playerBbmodels?.[skin];
      const sceneId = (scene as any).__mcSceneId ?? "";
      const mat = window.mcState?.skinMatCache?.[`${sceneId}_${skin}`];
      if (!bbmodel || !mat) {
        console.warn("[MiCraft] createFPArms: bbmodel or material not ready");
        return null;
      }

      const W = bbmodel.resolution.width,
        H = bbmodel.resolution.height;
      const SCALE = 1 / 16;

      const groupMap: Record<string, BbModelGroup> = {};
      bbmodel.groups.forEach((g) => {
        groupMap[g.name] = g;
      });
      const headGroup = groupMap["head"];

      const armEls: Record<string, BbModelElement> = {};
      bbmodel.elements.forEach((el) => {
        if (el.name === "rightArm" || el.name === "leftArm") armEls[el.name] = el;
      });

      const pivots: McFPArms["pivots"] = [];
      const meshes: McFPArms["meshes"] = [];

      for (const name of ["rightArm", "leftArm"] as const) {
        const el = armEls[name];
        if (!el) {
          console.warn("[MiCraft] FP arms: missing element", name);
          continue;
        }

        // X/Y derived from arm group origin relative to head centre in the bbmodel skeleton.
        // Z (forward depth) stays a fixed constant — bbmodel has no meaningful Z offset.
        const armGroup = groupMap[name];
        let px: number, py: number;
        if (armGroup && headGroup) {
          px = (armGroup.origin[0] - headGroup.origin[0]) * SCALE;
          py = (armGroup.origin[1] - headGroup.origin[1]) * SCALE;
        } else {
          const sign = name === "rightArm" ? 1 : -1;
          px = sign * 0.25;
          py = -0.125;
        }

        const pivot = new BABYLON.TransformNode(`fp_${name}`, scene);
        pivot.parent = camera as any;
        pivot.position = new BABYLON.Vector3(px, py, 0.45);

        const [fx, fy, fz] = el.from,
          [tx, ty, tz] = el.to;
        const mesh = BABYLON.MeshBuilder.CreateBox(
          `${name}_fp`,
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
        (mesh as any).alwaysSelectAsActiveMesh = true; // bypass frustum culling for camera-parented mesh
        mesh.parent = pivot;
        mesh.position = new BABYLON.Vector3(0, -0.375, 0); // arm hangs below shoulder pivot

        pivots.push({ node: pivot, name });
        meshes.push(mesh);
      }

      // Extract walk animation keyframes for arms from the bbmodel
      const walkAnimDef =
        bbmodel.animations?.find((a) => a.name?.toLowerCase().includes("walk")) ??
        bbmodel.animations?.find((a) => Object.keys(a.animators).length > 1);

      const walkAnim: McFPArms["walkAnim"] = {};
      if (walkAnimDef) {
        const nameToUuid: Record<string, string> = {};
        bbmodel.groups.forEach((g) => {
          nameToUuid[g.name] = g.uuid;
        });
        for (const bname of ["rightArm", "leftArm"] as const) {
          const uuid = nameToUuid[bname];
          if (!uuid) continue;
          const animator = walkAnimDef.animators[uuid];
          if (!animator) continue;
          const kfs = animator.keyframes.filter((k) => k.channel === "rotation").sort((a, b) => a.time - b.time);
          if (kfs.length > 0) walkAnim[bname] = { keyframes: kfs, length: walkAnimDef.length || 1 };
        }
      }

      meshes.forEach((m) => {
        m.isVisible = true;
      });
      console.log(`[MiCraft] FP arms created (${pivots.length} arms)`);
      const result: McFPArms = { pivots, meshes, walkAnim };
      window.mcState.currentFPArms = result;
      return result;
    },

    updateFPArms: (fpArms: McFPArms, isWalking: boolean): void => {
      if (!fpArms) return;
      const DEG = Math.PI / 180;
      for (const p of fpArms.pivots) {
        if (!isWalking) {
          p.node.rotation.x = 0;
          continue;
        }
        const bone = fpArms.walkAnim[p.name];
        if (bone) {
          const t = (Date.now() % (bone.length * 1000)) / (bone.length * 1000);
          p.node.rotation.x = interpAxis(bone.keyframes, t, "x") * DEG;
        } else {
          p.node.rotation.x = 0;
        }
      }
    },

    setFPArmsVisible: (fpArms: McFPArms, visible: boolean): void => {
      if (!fpArms) return;
      fpArms.meshes.forEach((m) => {
        m.isVisible = visible;
      });
    },

    disposeFPArms: (fpArms: McFPArms): void => {
      if (!fpArms) return;
      fpArms.meshes.forEach((m) => m.dispose());
      fpArms.pivots.forEach((p) => p.node.dispose());
      if (window.mcState.currentFPArms === fpArms) window.mcState.currentFPArms = null;
    },

    debugFPArms: (x?: number, y?: number, z?: number): void => {
      const fa = window.mcState.currentFPArms;
      if (!fa) {
        console.warn("[MiCraft] No FP arms active");
        return;
      }
      fa.pivots.forEach((p) => {
        const sign = p.name === "rightArm" ? 1 : -1;
        if (x !== undefined) p.node.position.x = sign * Math.abs(x);
        if (y !== undefined) p.node.position.y = y;
        if (z !== undefined) p.node.position.z = z;
      });
      const p0 = fa.pivots[0]?.node.position;
      if (p0)
        console.log(
          `[MiCraft] FP arm pivot → x=±${Math.abs(p0.x).toFixed(4)}  y=${p0.y.toFixed(4)}  z=${p0.z.toFixed(4)}`,
        );
    },
  };
}
