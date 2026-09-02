export interface E2eActions {
  moveForward(ms: number): void;
  moveBack(ms: number): void;
  moveLeft(ms: number): void;
  moveRight(ms: number): void;
  setLook(yaw: number, pitch: number): void;
  breakTargeted(): void;
  placeTargeted(): void;
  selectHotbar(i: number): void;
  /** Run a slash command ("/give …") or send chat, via the in-game console path. */
  runCommand(cmd: string): void;
}

export interface E2eSnapshot {
  ready: boolean;
  playerId: string;
  playerName: string;
  position: { x: number; y: number; z: number };
  serverPosition: { x: number; y: number; z: number };
  yaw: number;
  pitch: number;
  stance: string;
  hasPrediction: boolean;
  reconcile: { xz: number; y: number };
  loadedChunks: { cx: number; cz: number }[];
  meshedChunks: { cx: number; cz: number }[];
  inventory: Record<string, number>;
  targetBlock: { x: number; y: number; z: number } | null;
  remotePlayers: { id: string; name: string; x: number; y: number; z: number }[];
  /** Rolling window of `ServerMessage.Notification` texts, newest last. */
  notifications: string[];
  lastWorldUpdate: { x: number; y: number; z: number; block: string }[] | null;
  /** Current party, mirrored from the GroupSync message. `null` when not in a group. */
  group: {
    id: string;
    leaderId: string;
    leaderName: string;
    members: { playerId: string; playerName: string; online: boolean }[];
  } | null;
}

/**
 * `window.mcE2E`: the snapshot is filled incrementally by `mc.updateE2E(json)` (WASM),
 * `actions` is installed once by GameUI. Hence `Partial` — helpers that gate on
 * `ready === true` re-narrow to `E2eSnapshot`.
 */
export type McE2E = Partial<E2eSnapshot> & { actions?: E2eActions };
