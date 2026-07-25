import type { Scene } from "@babylonjs/core";
import { interpAxis } from "../player/playerModel";

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

    setNpcTransform: (model: McPlayerModel, x: number, y: number, z: number, yaw: number, isWalking: boolean): void => {
      model.root.position.x = x;
      model.root.position.y = y;
      model.root.position.z = z;
      model.root.rotation.y = yaw + (model._forwardOffset ?? Math.PI);

      const pn = model.pivotNodes;
      if (!pn) return;
      const DEG = Math.PI / 180;
      const wa = model.walkAnim ?? {};

      const PROC_AMP = 30;
      const PROC_PHASE: Record<string, number> = { rightArm: 0, leftArm: Math.PI, rightLeg: Math.PI, leftLeg: 0 };

      if (isWalking) {
        const animLen = wa["rightArm"]?.length ?? 1;
        const t = (Date.now() % (animLen * 1000)) / (animLen * 1000);
        for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
          if (!pn[bname]) continue;
          pn[bname].node.rotation.x = wa[bname]
            ? interpAxis(wa[bname].keyframes, t, "x") * DEG
            : PROC_AMP * DEG * Math.sin(t * 2 * Math.PI + (PROC_PHASE[bname] ?? 0));
        }
        return;
      }
      for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
        if (pn[bname]) pn[bname].node.rotation.x = 0;
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
