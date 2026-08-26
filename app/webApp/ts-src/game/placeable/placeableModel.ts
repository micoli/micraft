import type { Scene } from "@babylonjs/core";

interface PlaceableBbmodels {
  [type: string]: BbModel;
}

// Trimmed fork of vehicleModel.ts's registerVehicleModel: same bbmodel-fetch-and-cache pattern,
// serving free-standing placed objects (siege weapons in Phase A) instead of rail vehicles — see
// PlaceableManager.kt (game/) for the Kotlin side driving these bindings.
export function registerPlaceableModel(): Pick<
  McBindings,
  | "initPlaceableModels"
  | "isPlaceableModelsReady"
  | "createPlaceableModel"
  | "setPlaceableTransform"
  | "disposePlaceableModel"
> {
  return {
    initPlaceableModels: (placeableTypesJson: string): void => {
      window.mcState.placeableBbmodels = {} as PlaceableBbmodels;
      window.mcState.placeableModelsReady = false;

      let typeToFile: Record<string, string>;
      try {
        typeToFile = JSON.parse(placeableTypesJson);
      } catch {
        return;
      }

      const entries = Object.entries(typeToFile);
      if (entries.length === 0) {
        window.mcState.placeableModelsReady = true;
        return;
      }

      Promise.all(
        entries.map(([type, file]) =>
          // /api/models is a staticFiles mount (Application.kt), not an OpenAPI route.
          fetch(`/api/models/siege/weapons/${file}/${file}.bbmodel`)
            .then((r) => r.json())
            .then((data: BbModel) => {
              (window.mcState.placeableBbmodels as PlaceableBbmodels)[type] = data;
            })
            .catch((e) => {
              console.error(`[MiCraft] Failed to load placeable model ${file}`, e);
            }),
        ),
      ).then(() => {
        window.mcState.placeableModelsReady = true;
      });
    },

    isPlaceableModelsReady: (): boolean => !!(window.mcState && window.mcState.placeableModelsReady),

    createPlaceableModel: (scene: Scene, placeableType: string): McPlayerModel | null => {
      const bbmodel = (window.mcState?.placeableBbmodels as PlaceableBbmodels | undefined)?.[placeableType];
      if (!bbmodel) {
        console.warn("[MiCraft] Placeable bbmodel not found for type:", placeableType);
        return null;
      }
      return window.mc.createPlayerModelFromBbmodel(bbmodel, scene, `placeable_${placeableType}`, {});
    },

    setPlaceableTransform: (model: McPlayerModel, x: number, y: number, z: number, rotationStep: number): void => {
      model.root.position.x = x;
      model.root.position.y = y;
      model.root.position.z = z;
      // Negated: BabylonJS's rotation.y turns the opposite way round from the yaw convention
      // SiegeTrajectoryMath/SiegeWeaponManager.computeMuzzleAndVelocity use for the muzzle offset
      // and launch velocity — without this the model visibly spins one way while its trajectory
      // preview (and the real fired shot) goes out the other side.
      model.root.rotation.y = -(rotationStep * Math.PI) / 6; // 12 steps -> 30° increments
      model.root.rotation.x = 0;
      model.root.rotation.z = 0;
    },

    disposePlaceableModel: (model: McPlayerModel): void => {
      model.root.getChildMeshes(true).forEach((m) => m.dispose());
      Object.values(model.pivotNodes).forEach((p) => p.node.dispose());
      model.root.dispose();
    },
  };
}
