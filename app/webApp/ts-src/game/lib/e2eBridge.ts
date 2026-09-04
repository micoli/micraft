export interface E2eActions {
  moveForward(ms: number): void;
  moveBack(ms: number): void;
  moveLeft(ms: number): void;
  moveRight(ms: number): void;
  setLook(yaw: number, pitch: number): void;
  breakTargeted(): void;
  placeTargeted(): void;
  /** Hold / release the primary (left) mouse button — drives the real survival break/place path. */
  setBreaking(down: boolean): void;
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
  /** Named action blocks known to the client (drives ★ icons + Tab targeting). */
  actionBlocks: { name: string; x: number; y: number; z: number }[];
  /** The action block currently Tab-targeted, or null. */
  actionBlockTarget: { x: number; y: number; z: number } | null;
  lastWorldUpdate: { x: number; y: number; z: number; block: string }[] | null;
  /** Player vitals, mirrored from PlayerStatusUpdate. `null` before the first update. */
  playerStatus: {
    currentHp: number;
    maxHp: number;
    currentMana: number;
    maxMana: number;
    godMode: boolean;
  } | null;
  /** Set once the player has been downed (HP reached 0); cleared on respawn. */
  playerDowned: boolean;
  /** Current combat target, mirrored from CombatTargetUpdate. `null` when nothing targeted. */
  combatTarget: {
    targetId: string | null;
    displayName: string | null;
    currentHp: number;
    maxHp: number;
    level?: number;
  } | null;
  /** RPG character + derived stats, mirrored from CharacterSync. `null` for non-RPG players. */
  character: { character: unknown; derived: { maxHp?: number } & Record<string, unknown> } | null;
  /** Latest XP / level payload, mirrored from XpGained. `null` before the first kill. */
  xp: { level?: number; currentXp?: number; xpForNextLevel?: number } | null;
  /** Quest progress by id, mirrored from QuestSync / QuestUpdate. */
  quests: Record<string, { status: string; progress: Record<string, number>; completedAt: number | null }>;
  /** Active P2P trade, mirrored from Open/Update/Close trade messages. `null` when no trade. */
  trade: {
    tradeId: string;
    otherPlayer: string;
    myOffer: Record<string, number>;
    theirOffer: Record<string, number>;
    myAccepted: boolean;
    theirAccepted: boolean;
  } | null;
  /** Current guild, mirrored from GuildSync. `null` when not in a guild. */
  guild: {
    id: string;
    name: string;
    tag: string;
    ownerId: string;
    members: { playerId: string; playerName: string; rank: string; online: boolean }[];
    bank: Record<string, number>;
  } | null;
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
