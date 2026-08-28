import type { Scene } from "@babylonjs/core";

interface SiegeProjectileBbmodels {
  [type: string]: BbModel;
}

// Trimmed fork of placeableModel.ts: same bbmodel-fetch-and-cache pattern, serving flying siege
// projectiles (Phase C) instead of free-standing placed objects — see SiegeProjectileManager.kt
// (game/) for the Kotlin side driving these bindings. No rotationStep — a projectile's orientation
// isn't rendered yet (Phase D can add velocity-facing if desired), only its position.
export function registerSiegeProjectileModel(): Pick<
  McBindings,
  | "initSiegeProjectileModels"
  | "isSiegeProjectileModelsReady"
  | "createSiegeProjectileModel"
  | "setSiegeProjectileTransform"
  | "disposeSiegeProjectileModel"
> {
  return {
    initSiegeProjectileModels: (projectileTypesJson: string): void => {
      window.mcState.siegeProjectileBbmodels = {} as SiegeProjectileBbmodels;
      window.mcState.siegeProjectileModelsReady = false;

      let typeToFile: Record<string, string>;
      try {
        typeToFile = JSON.parse(projectileTypesJson);
      } catch {
        return;
      }

      const entries = Object.entries(typeToFile).filter(([, file]) => !!file);
      if (entries.length === 0) {
        window.mcState.siegeProjectileModelsReady = true;
        return;
      }

      Promise.all(
        entries.map(([type, file]) =>
          // /api/models is a staticFiles mount (Application.kt), not an OpenAPI route.
          fetch(`/api/models/siege/projectiles/${file}/${file}.bbmodel`)
            .then((r) => r.json())
            .then((data: BbModel) => {
              (window.mcState.siegeProjectileBbmodels as SiegeProjectileBbmodels)[type] = data;
            })
            .catch((e) => {
              console.error(`[MiCraft] Failed to load siege projectile model ${file}`, e);
            }),
        ),
      ).then(() => {
        window.mcState.siegeProjectileModelsReady = true;
      });
    },

    isSiegeProjectileModelsReady: (): boolean => !!(window.mcState && window.mcState.siegeProjectileModelsReady),

    createSiegeProjectileModel: (scene: Scene, projectileType: string): McPlayerModel | null => {
      const bbmodel = (window.mcState?.siegeProjectileBbmodels as SiegeProjectileBbmodels | undefined)?.[
        projectileType
      ];
      if (!bbmodel) {
        console.warn("[MiCraft] Siege projectile bbmodel not found for type:", projectileType);
        return null;
      }
      return window.mc.createPlayerModelFromBbmodel(bbmodel, scene, `siege_projectile_${projectileType}`, {});
    },

    setSiegeProjectileTransform: (model: McPlayerModel, x: number, y: number, z: number): void => {
      model.root.position.x = x;
      model.root.position.y = y;
      model.root.position.z = z;
    },

    disposeSiegeProjectileModel: (model: McPlayerModel): void => {
      model.root.getChildMeshes(true).forEach((m) => m.dispose());
      Object.values(model.pivotNodes).forEach((p) => p.node.dispose());
      model.root.dispose();
    },
  };
}
