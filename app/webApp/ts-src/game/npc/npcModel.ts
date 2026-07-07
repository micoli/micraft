import type { Scene } from "@babylonjs/core";
import { interpAxis } from "../player/playerModel";

interface NpcBbmodels {
  [type: string]: BbModel;
}

export function registerNpcModel(): Pick<
  McBindings,
  "initNpcModels" | "isNpcModelsReady" | "createNpcModel" | "setNpcTransform" | "disposeNpcModel" | "openNpcDialog"
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
        console.log("[MiCraft] NPC models loaded:", Object.keys(window.mcState.npcBbmodels as NpcBbmodels));
      });
    },

    isNpcModelsReady: (): boolean => !!(window.mcState && window.mcState.npcModelsReady),

    createNpcModel: (scene: Scene, npcType: string): McPlayerModel | null => {
      const bbmodel = (window.mcState?.npcBbmodels as NpcBbmodels | undefined)?.[npcType];
      if (!bbmodel) {
        console.warn("[MiCraft] NPC bbmodel not found for type:", npcType);
        return null;
      }
      return window.mc.createPlayerModelFromBbmodel(bbmodel, scene, `npc_${npcType}`);
    },

    setNpcTransform: (model: McPlayerModel, x: number, y: number, z: number, yaw: number, isWalking: boolean): void => {
      model.root.position.x = x;
      model.root.position.y = y;
      model.root.position.z = z;
      model.root.rotation.y = yaw + Math.PI;

      const pn = model.pivotNodes;
      if (!pn) return;
      const DEG = Math.PI / 180;
      const wa = model.walkAnim ?? {};

      if (isWalking) {
        const animLen = wa["rightArm"]?.length ?? 1;
        const t = (Date.now() % (animLen * 1000)) / (animLen * 1000);
        for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
          if (!pn[bname]) continue;
          pn[bname].node.rotation.x = (wa[bname] ? interpAxis(wa[bname].keyframes, t, "x") : 0) * DEG;
        }
        return;
      }
      for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
        if (pn[bname]) pn[bname].node.rotation.x = 0;
      }
    },

    disposeNpcModel: (model: McPlayerModel): void => {
      if (!model) return;
      model.root.getChildMeshes(true).forEach((m) => m.dispose());
      Object.values(model.pivotNodes).forEach((p: any) => p.node.dispose());
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
