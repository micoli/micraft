import type { LinesMesh, Scene, Vector3 } from "@babylonjs/core";

// 12 edges of a unit cube centred at origin, expanded by `h` on each side.
function cubeLines(h: number): [Vector3, Vector3][] {
  const V = (x: number, y: number, z: number) => new BABYLON.Vector3(x, y, z);
  return [
    [V(-h, -h, -h), V(h, -h, -h)],
    [V(h, -h, -h), V(h, -h, h)],
    [V(h, -h, h), V(-h, -h, h)],
    [V(-h, -h, h), V(-h, -h, -h)],
    [V(-h, h, -h), V(h, h, -h)],
    [V(h, h, -h), V(h, h, h)],
    [V(h, h, h), V(-h, h, h)],
    [V(-h, h, h), V(-h, h, -h)],
    [V(-h, -h, -h), V(-h, h, -h)],
    [V(h, -h, -h), V(h, h, -h)],
    [V(h, -h, h), V(h, h, h)],
    [V(-h, -h, h), V(-h, h, h)],
  ];
}

// 12 edges of an arbitrary axis-aligned box, expanded by `h` on each side.
export function boxLines(
  x0: number,
  y0: number,
  z0: number,
  x1: number,
  y1: number,
  z1: number,
  h = 0.002,
): [Vector3, Vector3][] {
  const V = (x: number, y: number, z: number) => new BABYLON.Vector3(x, y, z);
  const xa = x0 - h,
    xb = x1 + h,
    ya = y0 - h,
    yb = y1 + h,
    za = z0 - h,
    zb = z1 + h;
  return [
    [V(xa, ya, za), V(xb, ya, za)],
    [V(xb, ya, za), V(xb, ya, zb)],
    [V(xb, ya, zb), V(xa, ya, zb)],
    [V(xa, ya, zb), V(xa, ya, za)],
    [V(xa, yb, za), V(xb, yb, za)],
    [V(xb, yb, za), V(xb, yb, zb)],
    [V(xb, yb, zb), V(xa, yb, zb)],
    [V(xa, yb, zb), V(xa, yb, za)],
    [V(xa, ya, za), V(xa, yb, za)],
    [V(xb, ya, za), V(xb, yb, za)],
    [V(xb, ya, zb), V(xb, yb, zb)],
    [V(xa, ya, zb), V(xa, yb, zb)],
  ];
}

// Resolves the world-space outline (lines + mesh position) for a block's real footprint: a
// classic 1×1×1 cube centred on its cell, or — for multi-cell/fractional (lego) blocks — a box
// spanning the block's rotated brickSize footprint anchored at its placement offset. Shared by
// the hover outline (showTargetOutline) and the break overlay (showBreakOverlay) so casser un
// bloc lego décalé surligne la même empreinte que celle prévisualisée au survol.
function footprintLines(
  blockDef: McBlockDef | null,
  x: number,
  y: number,
  z: number,
  rotation: number,
  xOff: number,
  zOff: number,
): { lines: [Vector3, Vector3][]; meshPos: InstanceType<typeof BABYLON.Vector3> } {
  // brickSize is in half-voxel units (2 = 1 full voxel) — divide by 2 for the voxel-fraction /
  // voxel-count semantics used below.
  const bs = blockDef?.brickSize?.map((v) => v / 2);
  if (bs && (bs[0] !== 1 || bs[2] !== 1)) {
    const rot90 = rotation === 1 || rotation === 3;
    // Offsets are world-space — no axis swap needed here
    const fracX = bs[0] < 1 ? bs[0] : bs[0] > 1 ? 0.5 : 0;
    const fracZ = bs[2] < 1 ? bs[2] : bs[2] > 1 ? 0.5 : 0;
    const worldSizeX = rot90 ? (bs[2] < 1 ? bs[2] : Math.ceil(bs[2])) : bs[0] < 1 ? bs[0] : Math.ceil(bs[0]);
    const worldSizeY = Math.ceil(bs[1]);
    const worldSizeZ = rot90 ? (bs[0] < 1 ? bs[0] : Math.ceil(bs[0])) : bs[2] < 1 ? bs[2] : Math.ceil(bs[2]);
    const offsetX = xOff * fracX;
    const offsetZ = zOff * fracZ;
    return {
      lines: boxLines(x + offsetX, y, z + offsetZ, x + offsetX + worldSizeX, y + worldSizeY, z + offsetZ + worldSizeZ),
      meshPos: BABYLON.Vector3.Zero(),
    };
  }
  return { lines: cubeLines(0.502), meshPos: new BABYLON.Vector3(x + 0.5, y + 0.5, z + 0.5) };
}

export function registerTargeting(): Pick<
  McBindings,
  "showTargetOutline" | "hideTargetOutline" | "showBreakOverlay" | "hideBreakOverlay"
> {
  return {
    showTargetOutline: (
      scene: Scene,
      x: number,
      y: number,
      z: number,
      breakable: boolean,
      typeOrd = -1,
      rotation = 0,
      xOff = 0,
      zOff = 0,
    ): void => {
      if (window.mcState.targetMesh) {
        window.mcState.targetMesh.dispose();
        window.mcState.targetMesh = null;
      }
      const blockDef = typeOrd >= 0 ? window.mc.getBlockDef(typeOrd) : null;
      const { lines, meshPos } = footprintLines(blockDef, x, y, z, rotation, xOff, zOff);
      const ls = BABYLON.MeshBuilder.CreateLineSystem("targetOutline", { lines }, scene) as LinesMesh;
      ls.position = meshPos;
      ls.color = breakable ? new BABYLON.Color3(0, 0, 0) : new BABYLON.Color3(0.55, 0.55, 0.55);
      ls.isPickable = false;
      window.mcState.targetMesh = ls;
    },

    hideTargetOutline: (): void => {
      if (window.mcState.targetMesh) {
        window.mcState.targetMesh.dispose();
        window.mcState.targetMesh = null;
      }
    },

    showBreakOverlay: (
      scene: Scene,
      x: number,
      y: number,
      z: number,
      alpha: number,
      typeOrd = -1,
      rotation = 0,
      xOff = 0,
      zOff = 0,
    ): void => {
      const bpos = `${x},${y},${z},${typeOrd},${rotation},${xOff},${zOff}`;
      if (!window.mcState.breakMesh || window.mcState.breakMesh._bpos !== bpos) {
        if (window.mcState.breakMesh) window.mcState.breakMesh.dispose();
        const blockDef = typeOrd >= 0 ? window.mc.getBlockDef(typeOrd) : null;
        // Footprint-aware outline: englobes the real extent of a multi-cell/offset lego entity
        // instead of always drawing a fixed 1×1×1 cube — mirrors showTargetOutline's hover shape.
        const { lines, meshPos } = footprintLines(blockDef, x, y, z, rotation, xOff, zOff);
        const ls = BABYLON.MeshBuilder.CreateLineSystem("breakOverlay", { lines }, scene) as LinesMesh;
        ls.position = meshPos;
        ls.color = new BABYLON.Color3(0, 0, 0);
        ls.isPickable = false;
        window.mcState.breakMesh = ls as InstanceType<typeof BABYLON.AbstractMesh> & { _bpos?: string };
        window.mcState.breakMesh!._bpos = bpos;
      }
      window.mcState.breakMesh!.visibility = alpha;
    },

    hideBreakOverlay: (): void => {
      if (window.mcState.breakMesh) {
        window.mcState.breakMesh.dispose();
        window.mcState.breakMesh = null;
      }
    },
  };
}
