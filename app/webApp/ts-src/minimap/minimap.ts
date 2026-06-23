// @ts-nocheck
const MINIMAP_SIZE = 180;
const ZOOM_RADII = [0, 1, 2, 3]; // chunk radius: 1x1, 3x3 (default), 5x5, 7x7

// RGB base colors per BlockType ordinal — populated from RegistrySync
let minimapColors: [number, number, number][] = [];

let zoomIndex = 1;
const chunkSurfaces: Record<string, { topY: number[]; topBlock: number[] }> = {};
let frameCount = 0;

export function setMinimapColors(blocks: { minimapColor: [number, number, number] }[]): void {
  minimapColors = blocks.map(b => b.minimapColor ?? [128, 128, 128]);
}

export function registerMinimap(): void {
  window.mcCreateMinimap = (): void => {
    const c = document.createElement('canvas');
    c.id = 'mc-minimap';
    (c as HTMLCanvasElement).width = MINIMAP_SIZE;
    (c as HTMLCanvasElement).height = MINIMAP_SIZE;
    const host = document.getElementById('mc-minimap-host');
    if (host) {
      c.style.cssText = 'width:100%;height:100%;display:block;border-radius:6px;pointer-events:none';
      host.appendChild(c);
    } else {
      c.style.cssText = [
        'position:fixed;top:12px;left:12px',
        'border-radius:6px;pointer-events:none;z-index:999',
        'border:2px solid rgba(255,255,255,0.25)',
        'box-shadow:0 2px 8px rgba(0,0,0,0.5)',
      ].join(';');
      document.body.appendChild(c);
    }
  };

  window.mcSetMinimapChunk = (cx: number, cz: number, topYJson: string, topBlockJson: string): void => {
    chunkSurfaces[`${cx},${cz}`] = {
      topY: JSON.parse(topYJson),
      topBlock: JSON.parse(topBlockJson),
    };
  };

  window.mcClearMinimapChunk = (cx: number, cz: number): void => {
    delete chunkSurfaces[`${cx},${cz}`];
  };

  window.mcMinimapZoomIn = (): void => {
    if (zoomIndex > 0) zoomIndex--;
  };

  window.mcMinimapZoomOut = (): void => {
    if (zoomIndex < ZOOM_RADII.length - 1) zoomIndex++;
  };


  window.mcDrawMinimap = (playerX: number, playerZ: number): void => {
    frameCount++;
    if (frameCount % 4 !== 0) return;

    const canvas = document.getElementById('mc-minimap') as HTMLCanvasElement | null;
    if (!canvas) return;
    const ctx = canvas.getContext('2d')!;

    const radius = ZOOM_RADII[zoomIndex];
    const totalBlocks = (radius * 2 + 1) * 16;
    const pixPerBlock = MINIMAP_SIZE / totalBlocks;
    const halfBlocks = totalBlocks / 2;
    const playerCx = Math.floor(playerX / 16);
    const playerCz = Math.floor(playerZ / 16);

    const imgData = ctx.createImageData(MINIMAP_SIZE, MINIMAP_SIZE);
    const data = imgData.data;
    for (let i = 0; i < data.length; i += 4) {
      data[i] = 10; data[i + 1] = 10; data[i + 2] = 20; data[i + 3] = 180;
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
            const bz = wz - playerZ + halfBlocks;
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
                data[i] = r; data[i + 1] = g; data[i + 2] = b; data[i + 3] = 255;
              }
            }
          }
        }
      }
    }

    ctx.putImageData(imgData, 0, 0);

    // Player dot at center
    ctx.fillStyle = '#ff3333';
    ctx.beginPath();
    ctx.arc(MINIMAP_SIZE / 2, MINIMAP_SIZE / 2, 3, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 1;
    ctx.stroke();
  };
}
