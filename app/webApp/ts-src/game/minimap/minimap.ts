/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
const MINIMAP_SIZE = 180;
const ZOOM_RADII = [0, 1, 2, 3]; // chunk radius: 1x1, 3x3 (default), 5x5, 7x7

// RGB base colors per BlockType ordinal — populated from RegistrySync
let minimapColors: [number, number, number][] = [];

let zoomIndex = 1;
const chunkSurfaces: Record<string, { topY: number[]; topBlock: number[] }> = {};
const npcPositions: Map<string, { x: number; z: number }> = new Map();
const remotePlayers: Map<string, { x: number; z: number; yaw: number }> = new Map();
let frameCount = 0;

// Terrain raster base layer
let terrainPixels: ImageData | null = null;
let terrainImgCx = 0;
let terrainImgCz = 0;
let terrainImgRadius = 0;
let terrainFetching = false;
const terrainFetchCenter = { x: NaN, z: NaN };

// Road raster overlay
let roadImg: HTMLImageElement | null = null;
let roadImgCx = 0;
let roadImgCz = 0;
let roadImgRadius = 0;
let roadsFetching = false;
const roadsFetchCenter = { x: NaN, z: NaN };

// Biome borders overlay
let biomeBorderData: Array<{ x1: number; z1: number; x2: number; z2: number }> = [];
const biomeFetchCenter = { x: NaN, z: NaN };
let biomeFetching = false;

// Staircase surface points
let staircasePoints: Array<{ x: number; z: number }> = [];
let staircasesFetching = false;
let staircasesFetched = false;

function maybeRefetchTerrain(playerX: number, playerZ: number): void {
  if (terrainFetching) return;
  if (!isNaN(terrainFetchCenter.x) && Math.hypot(playerX - terrainFetchCenter.x, playerZ - terrainFetchCenter.z) < 200)
    return;
  terrainFetchCenter.x = playerX;
  terrainFetchCenter.z = playerZ;
  const radius = 400;
  terrainFetching = true;
  const cx = Math.round(playerX);
  const cz = Math.round(playerZ);
  fetch(`/api/map/terrain-raster.png?cx=${cx}&cz=${cz}&radius=${radius}`)
    .then((r) => {
      if (r.ok) return r.blob();
      terrainFetchCenter.x = NaN;
    })
    .then((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const img = new Image();
      img.onload = () => {
        const oc = new OffscreenCanvas(img.width, img.height);
        const oc_ctx = oc.getContext("2d")!;
        oc_ctx.drawImage(img, 0, 0);
        terrainPixels = oc_ctx.getImageData(0, 0, img.width, img.height);
        terrainImgCx = cx;
        terrainImgCz = cz;
        terrainImgRadius = radius;
        URL.revokeObjectURL(url);
      };
      img.onerror = () => {
        terrainFetchCenter.x = NaN;
        URL.revokeObjectURL(url);
      };
      img.src = url;
    })
    .catch(() => {
      terrainFetchCenter.x = NaN;
    })
    .finally(() => {
      terrainFetching = false;
    });
}

function maybeRefetchStaircases(): void {
  if (staircasesFetching || staircasesFetched) return;
  staircasesFetching = true;
  fetch("/api/map/staircases")
    .then((r) => {
      if (r.ok) return r.json();
    })
    .then((data) => {
      if (data) {
        staircasePoints = data;
        staircasesFetched = true;
      }
    })
    .catch(() => {})
    .finally(() => {
      staircasesFetching = false;
    });
}

function maybeRefetchRoads(playerX: number, playerZ: number): void {
  if (roadsFetching) return;
  if (!isNaN(roadsFetchCenter.x) && Math.hypot(playerX - roadsFetchCenter.x, playerZ - roadsFetchCenter.z) < 200)
    return;
  roadsFetchCenter.x = playerX;
  roadsFetchCenter.z = playerZ;
  const radius = 400;
  roadsFetching = true;
  const cx = Math.round(playerX),
    cz = Math.round(playerZ);
  fetch(`/api/map/road-raster.png?cx=${cx}&cz=${cz}&radius=${radius}`)
    .then((r) => {
      if (r.ok) return r.blob();
      roadsFetchCenter.x = NaN;
    })
    .then((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const img = new Image();
      img.onload = () => {
        if (roadImg?.src) URL.revokeObjectURL(roadImg.src);
        roadImg = img;
        roadImgCx = cx;
        roadImgCz = cz;
        roadImgRadius = radius;
      };
      img.onerror = () => {
        roadsFetchCenter.x = NaN;
        URL.revokeObjectURL(url);
      };
      img.src = url;
    })
    .catch(() => {
      roadsFetchCenter.x = NaN;
    })
    .finally(() => {
      roadsFetching = false;
    });
}

function maybeRefetchBiomeBorders(playerX: number, playerZ: number): void {
  if (biomeFetching) return;
  if (!isNaN(biomeFetchCenter.x) && Math.hypot(playerX - biomeFetchCenter.x, playerZ - biomeFetchCenter.z) < 200)
    return;
  biomeFetchCenter.x = playerX;
  biomeFetchCenter.z = playerZ;
  biomeFetching = true;
  const cx = Math.round(playerX),
    cz = Math.round(playerZ);
  fetch(`/api/map/voronoi-borders?cx=${cx}&cz=${cz}&radius=800`)
    .then((r) => {
      if (r.ok) return r.json();
      biomeFetchCenter.x = NaN;
    })
    .then((data) => {
      if (data) biomeBorderData = data;
    })
    .catch(() => {
      biomeFetchCenter.x = NaN;
    })
    .finally(() => {
      biomeFetching = false;
    });
}

interface MinimapWeatherZone {
  id: string;
  type: string;
  cx: number;
  cz: number;
  radius: number;
  intensity: number;
}
let weatherZones: MinimapWeatherZone[] = [];
let currentPlacementRotation = -1;

const WEATHER_COLORS: Record<string, string> = {
  RAIN: "rgba(80,120,255,0.25)",
  STORM: "rgba(80,0,160,0.3)",
  SNOW: "rgba(220,240,255,0.3)",
  FOG: "rgba(160,160,160,0.25)",
};
const WEATHER_STROKE: Record<string, string> = {
  RAIN: "rgba(80,120,255,0.7)",
  STORM: "rgba(80,0,160,0.7)",
  SNOW: "rgba(200,230,255,0.7)",
  FOG: "rgba(140,140,140,0.7)",
};
const WEATHER_LABELS: Record<string, string> = {
  RAIN: "🌧",
  STORM: "⛈",
  SNOW: "❄",
  FOG: "🌫",
};

export function setMinimapColors(blocks: { minimapColor: [number, number, number] }[]): void {
  minimapColors = blocks.map((b) => b.minimapColor ?? [128, 128, 128]);
}

export function registerMinimap(): Pick<
  McBindings,
  | "createMinimap"
  | "setMinimapChunk"
  | "clearMinimapChunk"
  | "minimapZoomIn"
  | "minimapZoomOut"
  | "setNpcOnMinimap"
  | "removeNpcFromMinimap"
  | "setPlayerOnMinimap"
  | "removePlayerFromMinimap"
  | "setMinimapWeather"
  | "setPlacementRotation"
  | "drawMinimap"
> {
  return {
    createMinimap: (): void => {
      document.getElementById("mc-minimap")?.remove();
      const c = document.createElement("canvas");
      c.id = "mc-minimap";
      (c as HTMLCanvasElement).width = MINIMAP_SIZE;
      (c as HTMLCanvasElement).height = MINIMAP_SIZE;
      c.style.cssText = "width:100%;height:100%;display:block;border-radius:6px;pointer-events:none";
      const tryMount = (): void => {
        const host = document.getElementById("mc-minimap-host");
        if (host) {
          host.appendChild(c);
        } else {
          requestAnimationFrame(tryMount);
        }
      };
      tryMount();
    },

    setMinimapChunk: (cx: number, cz: number, topYJson: string, topBlockJson: string): void => {
      chunkSurfaces[`${cx},${cz}`] = {
        topY: JSON.parse(topYJson),
        topBlock: JSON.parse(topBlockJson),
      };
    },

    clearMinimapChunk: (cx: number, cz: number): void => {
      delete chunkSurfaces[`${cx},${cz}`];
    },

    minimapZoomIn: (): void => {
      if (zoomIndex > 0) zoomIndex--;
    },

    minimapZoomOut: (): void => {
      if (zoomIndex < ZOOM_RADII.length - 1) zoomIndex++;
    },

    setNpcOnMinimap: (id: string, x: number, z: number): void => {
      npcPositions.set(id, { x, z });
    },

    removeNpcFromMinimap: (id: string): void => {
      npcPositions.delete(id);
    },

    setPlayerOnMinimap: (id: string, x: number, z: number, yaw: number): void => {
      remotePlayers.set(id, { x, z, yaw });
    },

    removePlayerFromMinimap: (id: string): void => {
      remotePlayers.delete(id);
    },

    setMinimapWeather: (json: string): void => {
      try {
        weatherZones = JSON.parse(json);
      } catch {
        weatherZones = [];
      }
    },

    setPlacementRotation: (rotation: number): void => {
      currentPlacementRotation = rotation;
    },

    drawMinimap: (playerX: number, playerZ: number, playerYaw: number): void => {
      frameCount++;
      if (frameCount % 4 !== 0) return;

      maybeRefetchTerrain(playerX, playerZ);
      maybeRefetchRoads(playerX, playerZ);
      maybeRefetchBiomeBorders(playerX, playerZ);
      maybeRefetchStaircases();

      const canvas = document.getElementById("mc-minimap") as HTMLCanvasElement | null;
      if (!canvas) return;
      const ctx = canvas.getContext("2d")!;

      const radius = ZOOM_RADII[zoomIndex];
      const totalBlocks = (radius * 2 + 1) * 16;
      const pixPerBlock = MINIMAP_SIZE / totalBlocks;
      const halfBlocks = totalBlocks / 2;
      const playerCx = Math.floor(playerX / 16);
      const playerCz = Math.floor(playerZ / 16);

      const imgData = ctx.createImageData(MINIMAP_SIZE, MINIMAP_SIZE);
      const data = imgData.data;
      for (let i = 0; i < data.length; i += 4) {
        data[i] = 10;
        data[i + 1] = 10;
        data[i + 2] = 20;
        data[i + 3] = 180;
      }

      // Terrain raster base layer from cached PNGs
      if (terrainPixels !== null) {
        const rasterOriginX = terrainImgCx - terrainImgRadius;
        const rasterOriginZ = terrainImgCz + terrainImgRadius; // top of raster (highest Z)
        for (let py = 0; py < MINIMAP_SIZE; py++) {
          for (let px = 0; px < MINIMAP_SIZE; px++) {
            const wx = Math.floor(playerX - halfBlocks + (px + 0.5) / pixPerBlock);
            const wz = Math.floor(playerZ + halfBlocks - (py + 0.5) / pixPerBlock); // Z flipped
            const rx = wx - rasterOriginX;
            const rz = rasterOriginZ - wz;
            if (rx < 0 || rx >= terrainPixels.width || rz < 0 || rz >= terrainPixels.height) continue;
            const srcIdx = (rz * terrainPixels.width + rx) * 4;
            if (terrainPixels.data[srcIdx + 3] === 0) continue;
            const dstIdx = (py * MINIMAP_SIZE + px) * 4;
            data[dstIdx] = terrainPixels.data[srcIdx];
            data[dstIdx + 1] = terrainPixels.data[srcIdx + 1];
            data[dstIdx + 2] = terrainPixels.data[srcIdx + 2];
            data[dstIdx + 3] = 255;
          }
        }
      }

      for (let dcx = -(radius + 1); dcx <= radius + 1; dcx++) {
        for (let dcz = -(radius + 1); dcz <= radius + 1; dcz++) {
          const cx = playerCx + dcx;
          const cz = playerCz + dcz;
          const chunk = chunkSurfaces[`${cx},${cz}`];
          if (!chunk) continue;
          for (let lx = 0; lx < 16; lx++) {
            for (let lz = 0; lz < 16; lz++) {
              const wx = cx * 16 + lx;
              const wz = cz * 16 + lz;
              const bx = wx - playerX + halfBlocks;
              const bz = playerZ - wz + halfBlocks; // Z flipped: +Z = haut de la minimap
              const px0 = Math.round(bx * pixPerBlock);
              const pz0 = Math.round(bz * pixPerBlock);
              const px1 = Math.round((bx + 1) * pixPerBlock);
              const pz1 = Math.round((bz + 1) * pixPerBlock);
              if (px1 <= 0 || px0 >= MINIMAP_SIZE || pz1 <= 0 || pz0 >= MINIMAP_SIZE) continue;

              const idx = lx * 16 + lz;
              const topY = chunk.topY[idx];
              const bt = chunk.topBlock[idx];
              const color = minimapColors[bt] ?? [128, 128, 128];
              const shade = 0.6 + 0.4 * Math.min(topY / 96, 1);
              const contour = topY % 8 === 0 ? 0.6 : 1.0;
              const r = Math.round(color[0] * shade * contour);
              const g = Math.round(color[1] * shade * contour);
              const b = Math.round(color[2] * shade * contour);

              for (let px = Math.max(0, px0); px < Math.min(MINIMAP_SIZE, px1); px++) {
                for (let pz = Math.max(0, pz0); pz < Math.min(MINIMAP_SIZE, pz1); pz++) {
                  const i = (pz * MINIMAP_SIZE + px) * 4;
                  data[i] = r;
                  data[i + 1] = g;
                  data[i + 2] = b;
                  data[i + 3] = 255;
                }
              }
            }
          }
        }
      }

      ctx.putImageData(imgData, 0, 0);

      // Biome border contours
      ctx.strokeStyle = "rgba(200,80,80,0.8)";
      ctx.lineWidth = Math.max(1, pixPerBlock);
      ctx.beginPath();
      for (const seg of biomeBorderData) {
        const sx1 = (seg.x1 - playerX + halfBlocks) * pixPerBlock;
        const sy1 = (playerZ - seg.z1 + halfBlocks) * pixPerBlock;
        const sx2 = (seg.x2 - playerX + halfBlocks) * pixPerBlock;
        const sy2 = (playerZ - seg.z2 + halfBlocks) * pixPerBlock;
        ctx.moveTo(sx1, sy1);
        ctx.lineTo(sx2, sy2);
      }
      ctx.stroke();

      // Precise road raster overlay
      if (roadImg !== null) {
        const tx = (roadImgCx - roadImgRadius - playerX + halfBlocks) * pixPerBlock;
        const tz = (playerZ - (roadImgCz + roadImgRadius) + halfBlocks) * pixPerBlock;
        const size = 2 * roadImgRadius * pixPerBlock;
        ctx.globalAlpha = 0.85;
        ctx.drawImage(roadImg, tx, tz, size, size);
        ctx.globalAlpha = 1.0;
      }

      // Weather zone overlays
      for (const zone of weatherZones) {
        const bx = zone.cx - playerX + halfBlocks;
        const bz = playerZ - zone.cz + halfBlocks; // Z flipped
        const px = bx * pixPerBlock;
        const pz = bz * pixPerBlock;
        const rPx = zone.radius * pixPerBlock;
        if (px + rPx < -4 || px - rPx > MINIMAP_SIZE + 4 || pz + rPx < -4 || pz - rPx > MINIMAP_SIZE + 4) continue;

        ctx.save();
        ctx.beginPath();
        ctx.arc(px, pz, rPx, 0, Math.PI * 2);
        ctx.fillStyle = WEATHER_COLORS[zone.type] ?? "rgba(128,128,128,0.2)";
        ctx.fill();
        ctx.strokeStyle = WEATHER_STROKE[zone.type] ?? "rgba(128,128,128,0.7)";
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.restore();

        if (rPx > 6) {
          ctx.font = "11px serif";
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText(WEATHER_LABELS[zone.type] ?? zone.type.charAt(0), px, pz);
          ctx.textAlign = "start";
          ctx.textBaseline = "alphabetic";
        }
      }

      // NPC dots
      for (const [, npc] of npcPositions) {
        const bx = npc.x - playerX + halfBlocks;
        const bz = playerZ - npc.z + halfBlocks; // Z flipped
        const px = bx * pixPerBlock;
        const pz = bz * pixPerBlock;
        if (px < -4 || px > MINIMAP_SIZE + 4 || pz < -4 || pz > MINIMAP_SIZE + 4) continue;
        ctx.fillStyle = "#ffaa00";
        ctx.beginPath();
        ctx.arc(px, pz, 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = "#000000";
        ctx.lineWidth = 0.5;
        ctx.stroke();
      }

      // Staircase surface exits (violet circles)
      for (const s of staircasePoints) {
        const bx = s.x - playerX + halfBlocks;
        const bz = playerZ - s.z + halfBlocks; // Z flipped
        const px = bx * pixPerBlock;
        const pz = bz * pixPerBlock;
        if (px < -6 || px > MINIMAP_SIZE + 6 || pz < -6 || pz > MINIMAP_SIZE + 6) continue;
        ctx.beginPath();
        ctx.arc(px, pz, 3, 0, Math.PI * 2);
        ctx.fillStyle = "rgba(160,80,220,0.5)";
        ctx.fill();
        ctx.strokeStyle = "#b060e0";
        ctx.lineWidth = 1;
        ctx.stroke();
      }

      // Remote player arrows
      for (const [, rp] of remotePlayers) {
        const bx = rp.x - playerX + halfBlocks;
        const bz = playerZ - rp.z + halfBlocks; // Z flipped
        const px = bx * pixPerBlock;
        const pz = bz * pixPerBlock;
        if (px < -8 || px > MINIMAP_SIZE + 8 || pz < -8 || pz > MINIMAP_SIZE + 8) continue;
        const rpYaw = rp.yaw + Math.PI;
        const radx = -Math.sin(rpYaw);
        const rady = Math.cos(rpYaw);
        const rperpX = -rady;
        const rperpY = radx;
        const rpLen = 6;
        const rpWidth = 3;
        const rpTipX = px + radx * rpLen;
        const rpTipY = pz + rady * rpLen;
        const rpB1x = px - radx * rpLen * 0.4 + rperpX * rpWidth;
        const rpB1y = pz - rady * rpLen * 0.4 + rperpY * rpWidth;
        const rpB2x = px - radx * rpLen * 0.4 - rperpX * rpWidth;
        const rpB2y = pz - rady * rpLen * 0.4 - rperpY * rpWidth;
        ctx.fillStyle = "#33ddff";
        ctx.beginPath();
        ctx.moveTo(rpTipX, rpTipY);
        ctx.lineTo(rpB1x, rpB1y);
        ctx.lineTo(rpB2x, rpB2y);
        ctx.closePath();
        ctx.fill();
        ctx.strokeStyle = "#000000";
        ctx.lineWidth = 0.8;
        ctx.stroke();
      }

      // Player direction arrow at center
      const cx = MINIMAP_SIZE / 2;
      const cy = MINIMAP_SIZE / 2;
      const arrowLen = 9;
      const arrowWidth = 4;
      // canvas: left=+X, up=+Z → arrow direction
      const yaw = playerYaw + Math.PI;
      const adx = -Math.sin(yaw);
      const ady = Math.cos(yaw);
      const perpX = -ady;
      const perpY = adx;
      const tipX = cx + adx * arrowLen;
      const tipY = cy + ady * arrowLen;
      const b1x = cx - adx * arrowLen * 0.4 + perpX * arrowWidth;
      const b1y = cy - ady * arrowLen * 0.4 + perpY * arrowWidth;
      const b2x = cx - adx * arrowLen * 0.4 - perpX * arrowWidth;
      const b2y = cy - ady * arrowLen * 0.4 - perpY * arrowWidth;

      ctx.fillStyle = "#ff3333";
      ctx.beginPath();
      ctx.moveTo(tipX, tipY);
      ctx.lineTo(b1x, b1y);
      ctx.lineTo(b2x, b2y);
      ctx.closePath();
      ctx.fill();
      ctx.strokeStyle = "#ffffff";
      ctx.lineWidth = 1;
      ctx.stroke();

      // Placement rotation arrow (ring around player, visible in place mode)
      if (currentPlacementRotation >= 0) {
        const rotAngle = currentPlacementRotation * (Math.PI / 2);
        const rdx = Math.sin(rotAngle);
        const rdy = -Math.cos(rotAngle);
        const ringR = 15;
        const rLen = 6;
        const rWidth = 3;
        const rBase = { x: cx + rdx * ringR, y: cy + rdy * ringR };
        const rtipX = rBase.x + rdx * rLen;
        const rtipY = rBase.y + rdy * rLen;
        const rperpX = -rdy;
        const rperpY = rdx;
        const rb1x = rBase.x - rdx * rLen * 0.4 + rperpX * rWidth;
        const rb1y = rBase.y - rdy * rLen * 0.4 + rperpY * rWidth;
        const rb2x = rBase.x - rdx * rLen * 0.4 - rperpX * rWidth;
        const rb2y = rBase.y - rdy * rLen * 0.4 - rperpY * rWidth;
        ctx.fillStyle = "#ffee00";
        ctx.beginPath();
        ctx.moveTo(rtipX, rtipY);
        ctx.lineTo(rb1x, rb1y);
        ctx.lineTo(rb2x, rb2y);
        ctx.closePath();
        ctx.fill();
        ctx.strokeStyle = "#000000";
        ctx.lineWidth = 1;
        ctx.stroke();
      }

      const gameTime: string = window.mcState.minimapGameTime ?? "";
      const playerY: number = window.mcState.minimapY ?? 0;
      const playerSpeed: number = window.mcState.minimapSpeed ?? 0;

      // Compass: yaw=0 → South, yaw=π → North
      const COMPASS_DIRS = [
        "N",
        "NNE",
        "NE",
        "ENE",
        "E",
        "ESE",
        "SE",
        "SSE",
        "S",
        "SSW",
        "SW",
        "WSW",
        "W",
        "WNW",
        "NW",
        "NNW",
      ];
      const compassIdx =
        Math.round(((((playerYaw % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)) / (Math.PI * 2)) * 16) % 16;
      const compassLabel = COMPASS_DIRS[compassIdx];

      // Game time (top center)
      ctx.font = "bold 10px monospace";
      ctx.textAlign = "center";
      ctx.textBaseline = "top";
      ctx.fillStyle = "rgba(0,0,0,0.6)";
      ctx.fillText(gameTime, MINIMAP_SIZE / 2 + 1, 4);
      ctx.fillStyle = "#ffffff";
      ctx.fillText(gameTime, MINIMAP_SIZE / 2, 3);

      // Speed bar below game time (1 box per 0.5 speed)
      const speedBoxCount = Math.round(playerSpeed / 0.5);
      const boxW = 5;
      const boxH = 4;
      const boxGap = 1;
      const totalBarW = speedBoxCount * (boxW + boxGap) - (speedBoxCount > 0 ? boxGap : 0);
      const barX = Math.round(MINIMAP_SIZE / 2 - totalBarW / 2);
      const barY = 16;
      for (let i = 0; i < speedBoxCount; i++) {
        const bx = barX + i * (boxW + boxGap);
        ctx.fillStyle = "rgba(0,0,0,0.5)";
        ctx.fillRect(bx + 1, barY + 1, boxW, boxH);
        ctx.fillStyle = "#44ff88";
        ctx.fillRect(bx, barY, boxW, boxH);
      }

      // Orientation above coordinates
      const coordText = `${Math.floor(playerX)}|${Math.floor(playerY)}|${Math.floor(playerZ)}`;
      ctx.font = "9px monospace";
      ctx.textAlign = "center";
      ctx.textBaseline = "bottom";
      ctx.fillStyle = "rgba(0,0,0,0.6)";
      ctx.fillText(compassLabel, MINIMAP_SIZE / 2 + 1, MINIMAP_SIZE - 12);
      ctx.fillStyle = "#ffdd44";
      ctx.fillText(compassLabel, MINIMAP_SIZE / 2, MINIMAP_SIZE - 13);
      ctx.fillStyle = "rgba(0,0,0,0.6)";
      ctx.fillText(coordText, MINIMAP_SIZE / 2 + 1, MINIMAP_SIZE - 2);
      ctx.fillStyle = "#ffffff";
      ctx.fillText(coordText, MINIMAP_SIZE / 2, MINIMAP_SIZE - 3);

      ctx.textAlign = "start";
      ctx.textBaseline = "alphabetic";
    },
  };
}
