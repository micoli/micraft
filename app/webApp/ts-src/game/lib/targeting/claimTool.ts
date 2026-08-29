import type { Scene } from "@babylonjs/core";
import { boxLines } from "./targeting";

// Draws/refreshes the live wireframe box between the fixed first corner and the block currently
// under the crosshair — called every frame from showTargetOutline (see targeting.ts) while a
// selection is in progress, so the preview tracks the player's look direction with no extra
// raycast of its own.
function updateClaimPreview(
  scene: Scene,
  corner1: { x: number; y: number; z: number },
  corner2: { x: number; y: number; z: number },
): void {
  if (window.mcState.claimPreviewMesh) {
    window.mcState.claimPreviewMesh.dispose();
    window.mcState.claimPreviewMesh = null;
  }
  const x0 = Math.min(corner1.x, corner2.x);
  const x1 = Math.max(corner1.x, corner2.x) + 1;
  const y0 = Math.min(corner1.y, corner2.y);
  const y1 = Math.max(corner1.y, corner2.y) + 1;
  const z0 = Math.min(corner1.z, corner2.z);
  const z1 = Math.max(corner1.z, corner2.z) + 1;
  const lines = boxLines(x0, y0, z0, x1, y1, z1);
  const ls = BABYLON.MeshBuilder.CreateLineSystem("claimPreview", { lines }, scene);
  ls.color = new BABYLON.Color3(0.2, 0.8, 1);
  ls.isPickable = false;
  window.mcState.claimPreviewMesh = ls;
}

function hideClaimPreview(): void {
  if (window.mcState.claimPreviewMesh) {
    window.mcState.claimPreviewMesh.dispose();
    window.mcState.claimPreviewMesh = null;
  }
}

/** Called from showTargetOutline/hideTargetOutline each frame — see targeting.ts. */
export function onTargetBlockChanged(scene: Scene | null, block: { x: number; y: number; z: number } | null): void {
  window.mcState.currentTargetBlock = block;
  if (scene && block && window.mcState.claimToolActive && window.mcState.claimCorner1) {
    updateClaimPreview(scene, window.mcState.claimCorner1, block);
  }
  if (!block) hideClaimPreview();
}

export function registerClaimTool(): Pick<McBindings, "claimMarkCorner" | "claimCancelSelection"> {
  return {
    // First press: fixes corner 1 at the current target block and starts the live preview.
    // Second press: fixes corner 2 at the current target block, sends ClaimCreate, resets.
    claimMarkCorner: (): void => {
      const target = window.mcState.currentTargetBlock;
      if (!target) {
        // Reuses the claim-denied toast path — no dedicated notification plumbing needed for a
        // purely client-side rejection.
        window.mc?.claimDenied?.("Look at a block within reach to mark a claim corner.");
        return;
      }
      if (!window.mcState.claimToolActive) {
        window.mcState.claimToolActive = true;
        window.mcState.claimCorner1 = target;
        window.mc?.claimDenied?.("First corner marked — look at the opposite corner and mark again.");
        return;
      }
      const corner1 = window.mcState.claimCorner1;
      window.mcState.claimToolActive = false;
      window.mcState.claimCorner1 = null;
      hideClaimPreview();
      if (!corner1) return;
      window.mcState.events.push(`claim_create:${JSON.stringify({ pos1: corner1, pos2: target })}`);
    },

    claimCancelSelection: (): void => {
      window.mcState.claimToolActive = false;
      window.mcState.claimCorner1 = null;
      hideClaimPreview();
    },
  };
}
