import type { Scene } from "@babylonjs/core";

interface NpcBbmodels {
  [type: string]: BbModel;
}

export function registerNpcModel(): void {
  window.mcInitNpcModels = (npcTypesJson: string): void => {
    window.__mc = window.__mc || ({} as any);
    window.__mc.npcBbmodels = {} as NpcBbmodels;
    window.__mc.npcModelsReady = false;

    let typeToFile: Record<string, string>;
    try {
      typeToFile = JSON.parse(npcTypesJson);
    } catch {
      return;
    }

    const entries = Object.entries(typeToFile);
    if (entries.length === 0) {
      window.__mc.npcModelsReady = true;
      return;
    }

    Promise.all(
      entries.map(([type, file]) =>
        fetch(`/models/${file}`)
          .then((r) => r.json())
          .then((data: BbModel) => {
            (window.__mc.npcBbmodels as NpcBbmodels)[type] = data;
          })
          .catch((e) => {
            console.error(`[MiCraft] Failed to load NPC model ${file}`, e);
          }),
      ),
    ).then(() => {
      window.__mc.npcModelsReady = true;
      console.log("[MiCraft] NPC models loaded:", Object.keys(window.__mc.npcBbmodels as NpcBbmodels));
    });
  };

  window.mcIsNpcModelsReady = (): boolean => !!(window.__mc && window.__mc.npcModelsReady);

  window.mcCreateNpcModel = (scene: Scene, npcType: string): McPlayerModel | null => {
    const bbmodel = (window.__mc?.npcBbmodels as NpcBbmodels | undefined)?.[npcType];
    if (!bbmodel) {
      console.warn("[MiCraft] NPC bbmodel not found for type:", npcType);
      return null;
    }
    return window.mcCreatePlayerModelFromBbmodel(bbmodel, scene);
  };

  window.mcSetNpcTransform = (
    model: McPlayerModel,
    x: number,
    y: number,
    z: number,
    yaw: number,
    isWalking: boolean,
  ): void => {
    model.root.position.x = x;
    model.root.position.y = y;
    model.root.position.z = z;
    model.root.rotation.y = yaw + Math.PI;

    const pn = model.pivotNodes;
    if (!pn) return;
    const DEG = Math.PI / 180;
    const wa = model.walkAnim ?? {};

    if (!isWalking) {
      const animLen = wa["rightArm"]?.length ?? 1;
      const t = (Date.now() % (animLen * 1000)) / (animLen * 1000);
      for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
        if (!pn[bname]) continue;
        const interpAxis = (window as any).__mcInterpAxis ?? (() => 0);
        pn[bname].node.rotation.x = (wa[bname] ? interpAxis(wa[bname].keyframes, t, "x") : 0) * DEG;
      }
      return;
    }
    for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
      if (pn[bname]) pn[bname].node.rotation.x = 0;
    }
  };

  window.mcDisposeNpcModel = (model: McPlayerModel): void => {
    if (!model) return;
    model.root.getChildMeshes(true).forEach((m) => m.dispose());
    Object.values(model.pivotNodes).forEach((p: any) => p.node.dispose());
    model.root.dispose();
  };

  window.mcOpenNpcDialog = (json: string): void => {
    try {
      const data = JSON.parse(json);
      (window as any).__mcDispatch?.({ type: "npc_dialog_open", payload: data });
    } catch {
      /* ignore */
    }
  };
}
