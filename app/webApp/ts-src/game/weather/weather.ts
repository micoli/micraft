// @ts-nocheck
interface WeatherZone {
  id: string;
  type: "RAIN" | "STORM" | "SNOW" | "FOG" | string;
  cx: number;
  cz: number;
  radius: number;
  intensity: number;
}

const CLOUD_Y = 1034;
const FOG_DEFAULT_END = 40;
const FOG_DEFAULT_START = 24;

let activeZones: WeatherZone[] = [];

// Per-type particle state
interface ParticleState {
  mesh: any;
  matrices: Float32Array;
  offsets: Float32Array; // x, y, z per particle (ox, oy, oz)
  count: number;
  fallSpeed: number;
  spread: number;
  heightSpan: number;
}

let rainParticles: ParticleState | null = null;
let snowParticles: ParticleState | null = null;
let currentWeatherType: string = "NONE";

// Cloud meshes keyed by zone id
const cloudMeshes: Map<string, any> = new Map();

let stormFlashCounter = 0;

function createParticleMesh(scene: any, name: string, w: number, h: number): any {
  const positions = [-w / 2, -h / 2, 0, w / 2, -h / 2, 0, w / 2, h / 2, 0, -w / 2, h / 2, 0];
  const indices = [0, 1, 2, 0, 2, 3, 2, 1, 0, 3, 2, 0];
  const normals: number[] = [];
  BABYLON.VertexData.ComputeNormals(positions, indices, normals);
  const vd = new BABYLON.VertexData();
  vd.positions = positions;
  vd.indices = indices;
  vd.normals = normals;

  const mesh = new BABYLON.Mesh(name, scene);
  vd.applyToMesh(mesh);
  mesh.isPickable = false;

  const mat = new BABYLON.StandardMaterial(name + "_mat", scene);
  mat.disableLighting = true;
  mat.backFaceCulling = false;
  mesh.material = mat;
  return mesh;
}

function initRainParticles(scene: any, count: number, color: [number, number, number]): ParticleState {
  const mesh = createParticleMesh(scene, "mc_rain", 0.05, 0.8);
  const mat = mesh.material;
  mat.emissiveColor = new BABYLON.Color3(...color);
  mat.alpha = 0.55;
  (mat as any).fogEnabled = false;

  const offsets = new Float32Array(count * 3);
  const spread = 20;
  const heightSpan = 25;
  for (let i = 0; i < count; i++) {
    offsets[i * 3] = (Math.random() - 0.5) * spread * 2;
    offsets[i * 3 + 1] = Math.random() * heightSpan;
    offsets[i * 3 + 2] = (Math.random() - 0.5) * spread * 2;
  }

  const matrices = new Float32Array(count * 16);
  mesh.thinInstanceSetBuffer("matrix", matrices, 16);

  return { mesh, matrices, offsets, count, fallSpeed: 0.6, spread, heightSpan };
}

function initSnowParticles(scene: any, count: number): ParticleState {
  const mesh = createParticleMesh(scene, "mc_snow", 0.3, 0.3);
  const mat = mesh.material;
  mat.emissiveColor = new BABYLON.Color3(0.92, 0.95, 1.0);
  mat.alpha = 0.7;
  (mat as any).fogEnabled = false;

  const offsets = new Float32Array(count * 3);
  const spread = 22;
  const heightSpan = 25;
  for (let i = 0; i < count; i++) {
    offsets[i * 3] = (Math.random() - 0.5) * spread * 2;
    offsets[i * 3 + 1] = Math.random() * heightSpan;
    offsets[i * 3 + 2] = (Math.random() - 0.5) * spread * 2;
  }

  const matrices = new Float32Array(count * 16);
  mesh.thinInstanceSetBuffer("matrix", matrices, 16);

  return { mesh, matrices, offsets, count, fallSpeed: 0.12, spread, heightSpan };
}

function updateParticles(state: ParticleState, px: number, py: number, pz: number): void {
  const { offsets, matrices, count, fallSpeed, spread, heightSpan } = state;
  const bottom = -10;
  const driftX = (Math.random() - 0.5) * 0.02;
  const driftZ = (Math.random() - 0.5) * 0.02;

  for (let i = 0; i < count; i++) {
    offsets[i * 3 + 1] -= fallSpeed;
    offsets[i * 3] += driftX;
    offsets[i * 3 + 2] += driftZ;

    if (offsets[i * 3 + 1] < bottom) {
      offsets[i * 3] = (Math.random() - 0.5) * spread * 2;
      offsets[i * 3 + 1] = heightSpan + Math.random() * 5;
      offsets[i * 3 + 2] = (Math.random() - 0.5) * spread * 2;
    }

    const wx = px + offsets[i * 3];
    const wy = py + offsets[i * 3 + 1];
    const wz = pz + offsets[i * 3 + 2];

    const base = i * 16;
    matrices[base] = 1;
    matrices[base + 1] = 0;
    matrices[base + 2] = 0;
    matrices[base + 3] = 0;
    matrices[base + 4] = 0;
    matrices[base + 5] = 1;
    matrices[base + 6] = 0;
    matrices[base + 7] = 0;
    matrices[base + 8] = 0;
    matrices[base + 9] = 0;
    matrices[base + 10] = 1;
    matrices[base + 11] = 0;
    matrices[base + 12] = wx;
    matrices[base + 13] = wy;
    matrices[base + 14] = wz;
    matrices[base + 15] = 1;
  }

  state.mesh.thinInstanceSetBuffer("matrix", matrices, 16);
}

function hideParticles(state: ParticleState | null): void {
  if (state) state.mesh.setEnabled(false);
}

function syncCloudMeshes(scene: any): void {
  const currentIds = new Set(activeZones.filter((z) => z.type === "RAIN" || z.type === "STORM").map((z) => z.id));

  // Remove clouds for gone zones
  for (const [id, mesh] of cloudMeshes) {
    if (!currentIds.has(id)) {
      mesh.dispose();
      cloudMeshes.delete(id);
    }
  }

  // Add clouds for new zones
  for (const zone of activeZones) {
    if (zone.type !== "RAIN" && zone.type !== "STORM") continue;
    if (cloudMeshes.has(zone.id)) continue;

    const cloud = BABYLON.MeshBuilder.CreateDisc(
      "mc_cloud_" + zone.id,
      { radius: zone.radius * 0.8, tessellation: 24 },
      scene,
    );
    cloud.position = new BABYLON.Vector3(zone.cx, CLOUD_Y, zone.cz);
    cloud.rotation.x = Math.PI / 2;
    cloud.isPickable = false;

    const mat = new BABYLON.StandardMaterial("mc_cloud_mat_" + zone.id, scene);
    mat.emissiveColor = zone.type === "STORM" ? new BABYLON.Color3(0.2, 0.1, 0.25) : new BABYLON.Color3(0.4, 0.4, 0.45);
    mat.alpha = 0.55;
    mat.backFaceCulling = false;
    (mat as any).fogEnabled = false;
    cloud.material = mat;

    cloudMeshes.set(zone.id, cloud);
  }
}

function playerZone(px: number, pz: number): WeatherZone | null {
  let best: WeatherZone | null = null;
  let bestDist = Infinity;
  for (const z of activeZones) {
    const dx = z.cx - px;
    const dz = z.cz - pz;
    const dist = Math.sqrt(dx * dx + dz * dz);
    if (dist < z.radius && dist < bestDist) {
      bestDist = dist;
      best = z;
    }
  }
  return best;
}

export function registerWeather(): Pick<McBindings, "setWeatherZones" | "updateWeather"> {
  return {
    setWeatherZones: (json: string): void => {
      try {
        activeZones = JSON.parse(json);
      } catch {
        activeZones = [];
      }
      // Share with minimap
      window.mc.setMinimapWeather(json);
    },

    updateWeather: (scene: any, px: number, py: number, pz: number): void => {
      const underground = (window.mcState as any).caveFactor !== undefined && (window.mcState as any).caveFactor < 0.9;
      if (underground) {
        hideParticles(rainParticles);
        hideParticles(snowParticles);
        for (const mesh of cloudMeshes.values()) mesh.setEnabled(false);
        return;
      }
      for (const mesh of cloudMeshes.values()) mesh.setEnabled(true);

      syncCloudMeshes(scene);

      const zone = playerZone(px, pz);
      const newType = zone ? zone.type : "NONE";

      // Transition: hide old particles if type changed
      if (newType !== currentWeatherType) {
        hideParticles(rainParticles);
        hideParticles(snowParticles);
        // Restore fog
        scene.fogStart = FOG_DEFAULT_START;
        scene.fogEnd = FOG_DEFAULT_END;
        currentWeatherType = newType;
      }

      if (!zone) return;

      switch (zone.type) {
        case "RAIN": {
          if (!rainParticles) {
            rainParticles = initRainParticles(scene, 2000, [0.7, 0.85, 1.0]);
          }
          rainParticles.mesh.setEnabled(true);
          updateParticles(rainParticles, px, py, pz);
          break;
        }
        case "STORM": {
          if (!rainParticles) {
            rainParticles = initRainParticles(scene, 3500, [0.6, 0.7, 0.9]);
          }
          rainParticles.mesh.setEnabled(true);
          updateParticles(rainParticles, px, py, pz);

          // Lightning flash
          stormFlashCounter++;
          if (stormFlashCounter > 120 + Math.random() * 200) {
            stormFlashCounter = 0;
            const hemi = window.mcState.hemiLight;
            if (hemi) {
              const orig = hemi.intensity;
              hemi.intensity = Math.min(2.5, orig * 3);
              setTimeout(() => {
                if (hemi) hemi.intensity = orig;
              }, 80);
            }
          }
          break;
        }
        case "SNOW": {
          if (!snowParticles) {
            snowParticles = initSnowParticles(scene, 1500);
          }
          snowParticles.mesh.setEnabled(true);
          updateParticles(snowParticles, px, py, pz);
          break;
        }
        case "FOG": {
          const fogEnd = FOG_DEFAULT_START + (1 - zone.intensity) * (FOG_DEFAULT_END - FOG_DEFAULT_START);
          scene.fogEnd = Math.max(8, fogEnd);
          scene.fogStart = Math.max(2, scene.fogEnd * 0.3);
          break;
        }
      }
    },
  };
}
