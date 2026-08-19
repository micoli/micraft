import type { Scene } from "@babylonjs/core";

interface VehicleBbmodels {
  [type: string]: BbModel;
}

// Trimmed fork of npcModel.ts's registerNpcModel: same bbmodel-fetch-and-cache pattern, minus
// walk-bone animation (a rail vehicle never walks) — see VehicleManager.kt (game/) for the Kotlin
// side driving these bindings.
export function registerVehicleModel(): Pick<
  McBindings,
  "initVehicleModels" | "isVehicleModelsReady" | "createVehicleModel" | "setVehicleTransform" | "disposeVehicleModel"
> {
  return {
    initVehicleModels: (vehicleTypesJson: string): void => {
      window.mcState.vehicleBbmodels = {} as VehicleBbmodels;
      window.mcState.vehicleModelsReady = false;

      let typeToFile: Record<string, string>;
      try {
        typeToFile = JSON.parse(vehicleTypesJson);
      } catch {
        return;
      }

      const entries = Object.entries(typeToFile);
      if (entries.length === 0) {
        window.mcState.vehicleModelsReady = true;
        return;
      }

      Promise.all(
        entries.map(([type, file]) =>
          // /api/models is a staticFiles mount (Application.kt), not an OpenAPI route.
          fetch(`/api/models/vehicles/${file}/${file}.bbmodel`)
            .then((r) => r.json())
            .then((data: BbModel) => {
              (window.mcState.vehicleBbmodels as VehicleBbmodels)[type] = data;
            })
            .catch((e) => {
              console.error(`[MiCraft] Failed to load vehicle model ${file}`, e);
            }),
        ),
      ).then(() => {
        window.mcState.vehicleModelsReady = true;
      });
    },

    isVehicleModelsReady: (): boolean => !!(window.mcState && window.mcState.vehicleModelsReady),

    createVehicleModel: (scene: Scene, vehicleType: string): McPlayerModel | null => {
      const bbmodel = (window.mcState?.vehicleBbmodels as VehicleBbmodels | undefined)?.[vehicleType];
      if (!bbmodel) {
        console.warn("[MiCraft] Vehicle bbmodel not found for type:", vehicleType);
        return null;
      }
      return window.mc.createPlayerModelFromBbmodel(bbmodel, scene, `vehicle_${vehicleType}`, {});
    },

    setVehicleTransform: (model: McPlayerModel, x: number, y: number, z: number, yaw: number, pitch: number): void => {
      model.root.position.x = x;
      model.root.position.y = y;
      model.root.position.z = z;
      // Babylon's default Euler order (YXZ) applies rotation.y before rotation.x, so pitch tilts
      // around the vehicle's own already-yawed forward axis instead of the world X axis.
      model.root.rotation.y = yaw;
      model.root.rotation.x = pitch;
    },

    disposeVehicleModel: (model: McPlayerModel): void => {
      model.root.getChildMeshes(true).forEach((m) => m.dispose());
      Object.values(model.pivotNodes).forEach((p) => p.node.dispose());
      model.root.dispose();
    },
  };
}
