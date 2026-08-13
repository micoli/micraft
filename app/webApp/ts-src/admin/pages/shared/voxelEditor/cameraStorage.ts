export interface StoredCameraState {
  alpha: number;
  beta: number;
  radius: number;
  targetX: number;
  targetY: number;
  targetZ: number;
}

// storageKey is the full localStorage key (callers namespace it, e.g. `instanceEditorCamera:${zoneId}`
// or `sceneEditorCamera:${sceneId}`) so camera positions from the two editors never collide.
export function loadCameraState(storageKey: string): StoredCameraState | null {
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return null;
    return JSON.parse(raw) as StoredCameraState;
  } catch {
    return null;
  }
}

export function saveCameraState(storageKey: string, state: StoredCameraState) {
  try {
    localStorage.setItem(storageKey, JSON.stringify(state));
  } catch {
    // localStorage unavailable (private mode / quota) — camera position just won't persist.
  }
}
