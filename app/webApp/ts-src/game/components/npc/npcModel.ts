import type { Scene } from "@babylonjs/core";
import { interpAxis } from "../../lib/player/playerModel";
import { collectLimbBones } from "../../lib/player/limbBones";

interface NpcBbmodels {
  [type: string]: BbModel;
}

function computeForwardOffset(bbmodel: BbModel): number {
  const elemMap: Record<string, { from: number[]; to: number[] }> = {};
  for (const e of bbmodel.elements) {
    if (e.uuid && e.from && e.to) elemMap[e.uuid] = e;
  }
  const groupNames: Record<string, string> = {};
  for (const g of bbmodel.groups) {
    if (g?.uuid && g?.name) groupNames[g.uuid] = g.name;
  }

  let hX = 0,
    hZ = 0,
    hN = 0,
    aX = 0,
    aZ = 0,
    aN = 0;

  type OutlinerNode = string | { uuid?: string; children?: OutlinerNode[] };
  function walk(nodes: OutlinerNode[], inHead: boolean): void {
    for (const node of nodes) {
      if (typeof node === "string") {
        const e = elemMap[node];
        if (!e) continue;
        const cx = (e.from[0] + e.to[0]) / 2;
        const cz = (e.from[2] + e.to[2]) / 2;
        aX += cx;
        aZ += cz;
        aN++;
        if (inHead) {
          hX += cx;
          hZ += cz;
          hN++;
        }
      } else if (node && typeof node === "object") {
        const name = groupNames[node.uuid ?? ""] ?? "";
        walk(node.children ?? [], inHead || name.toLowerCase().includes("head"));
      }
    }
  }

  walk((bbmodel.outliner ?? []) as OutlinerNode[], false);
  if (hN === 0 || aN === 0) return Math.PI;
  return -Math.atan2(hX / hN - aX / aN, hZ / hN - aZ / aN);
}

export function registerNpcModel(): Pick<
  McBindings,
  | "initNpcModels"
  | "initNpcWalkBones"
  | "isNpcModelsReady"
  | "createNpcModel"
  | "setNpcTransform"
  | "setNpcScale"
  | "disposeNpcModel"
  | "openNpcDialog"
> {
  return {
    initNpcModels: (npcTypesJson: string): void => {
      window.mcState.npcBbmodels = {} as NpcBbmodels;
      window.mcState.npcModelsReady = false;

      let typeToFile: Record<string, string>;
      try {
        typeToFile = JSON.parse(npcTypesJson);
      } catch {
        return;
      }

      const entries = Object.entries(typeToFile);
      if (entries.length === 0) {
        window.mcState.npcModelsReady = true;
        return;
      }

      Promise.all(
        entries.map(([type, file]) =>
          // /api/models is a staticFiles mount (Application.kt), not an OpenAPI route.
          fetch(`/api/models/entities/${file}/${file}.bbmodel`)
            .then((r) => r.json())
            .then((data: BbModel) => {
              (window.mcState.npcBbmodels as NpcBbmodels)[type] = data;
            })
            .catch((e) => {
              console.error(`[MiCraft] Failed to load NPC model ${file}`, e);
            }),
        ),
      ).then(() => {
        window.mcState.npcModelsReady = true;
        console.log(
          "[MiCraft] NPC models loaded:",
          JSON.stringify(Object.keys(window.mcState.npcBbmodels as NpcBbmodels)),
        );
      });
    },

    initNpcWalkBones: (json: string): void => {
      try {
        window.mcState.npcWalkBones = JSON.parse(json);
      } catch {
        // ignore
      }
    },

    isNpcModelsReady: (): boolean => !!(window.mcState && window.mcState.npcModelsReady),

    createNpcModel: (scene: Scene, npcType: string): McPlayerModel | null => {
      const bbmodel = (window.mcState?.npcBbmodels as NpcBbmodels | undefined)?.[npcType];
      if (!bbmodel) {
        console.warn("[MiCraft] NPC bbmodel not found for type:", npcType);
        return null;
      }
      const aliases = window.mcState.npcWalkBones?.[npcType] ?? {};
      const model = window.mc.createPlayerModelFromBbmodel(bbmodel, scene, `npc_${npcType}`, aliases);
      model._forwardOffset = computeForwardOffset(bbmodel);
      return model;
    },

    setNpcTransform: (
      model: McPlayerModel,
      x: number,
      y: number,
      z: number,
      yaw: number,
      isWalking: boolean,
      isFlying?: boolean,
    ): void => {
      model.root.position.x = x;
      model.root.position.y = y;
      model.root.position.z = z;
      model.root.rotation.y = yaw + (model._forwardOffset ?? Math.PI);

      const pn = model.pivotNodes;
      if (!pn) return;
      const DEG = Math.PI / 180;
      const wa = model.animations?.walking_forward ?? {};

      const PROC_AMP = 30;
      const limbs = collectLimbBones(pn);

      const restWings = (): void => {
        for (const bname of ["rightWing", "leftWing"] as const) {
          const b = pn[bname];
          if (!b) continue;
          b.node.rotation.x = b.restRotation?.[0] ?? 0;
          b.node.rotation.z = b.restRotation?.[2] ?? 0;
        }
      };

      // Wing-flap: birds only, played whenever the NPC is airborne. Wings roll (z) around the
      // shoulder in mirror; the walk cycle (legs) is skipped.
      if (isFlying && (pn.rightWing || pn.leftWing)) {
        const FLAP_HZ = 2.6;
        const FLAP_AMP = 42 * DEG;
        const flap = Math.sin((Date.now() / 1000) * FLAP_HZ * 2 * Math.PI) * FLAP_AMP;
        for (const [bname, sign] of [
          ["rightWing", 1],
          ["leftWing", -1],
        ] as const) {
          const b = pn[bname];
          if (!b) continue;
          const rest = b.restRotation ?? [0, 0, 0];
          b.node.rotation.x = rest[0];
          b.node.rotation.z = rest[2] + sign * flap;
        }
        for (const { name: bname } of limbs) {
          if (pn[bname]) pn[bname].node.rotation.x = pn[bname].restRotation?.[0] ?? 0;
        }
        return;
      }

      restWings();

      if (isWalking) {
        const animLen = Math.max(wa["rightArm"]?.length ?? 1, 1e-3);
        // interpAxis samples in clip seconds; the procedural fallback wants a normalised 0..1 phase.
        const tSec = (Date.now() % (animLen * 1000)) / 1000;
        const phase = tSec / animLen;
        for (const { name: bname, phase: procPhase } of limbs) {
          const restX = pn[bname].restRotation?.[0] ?? 0;
          pn[bname].node.rotation.x =
            restX +
            (wa[bname]
              ? interpAxis(wa[bname].keyframes, tSec, "x") * DEG
              : PROC_AMP * DEG * Math.sin(phase * 2 * Math.PI + procPhase));
        }
        return;
      }
      for (const { name: bname } of limbs) {
        pn[bname].node.rotation.x = pn[bname].restRotation?.[0] ?? 0;
      }
    },

    setNpcScale: (model: McPlayerModel, scale: number): void => {
      if (!model?.root) return;
      model.root.scaling.setAll(scale);
    },

    disposeNpcModel: (model: McPlayerModel): void => {
      if (!model) return;
      model.root.getChildMeshes(true).forEach((m) => m.dispose());
      Object.values(model.pivotNodes).forEach((p) => p.node.dispose());
      model.root.dispose();
    },

    openNpcDialog: (json: string): void => {
      try {
        const data = JSON.parse(json);
        window.mcState.dispatch?.({ type: "npc_dialog_open", payload: data });
      } catch {
        /* ignore */
      }
    },
  };
}
