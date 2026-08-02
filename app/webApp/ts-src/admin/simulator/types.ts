// Mirrors of the Kotlin @Serializable DTOs in
// server/src/main/kotlin/org/micoli/micraft/simulation/SimulationProtocol.kt

import type { TranslationKey } from "../i18n";

export interface NpcTuning {
  wanderPauseTicksMin: number;
  wanderPauseTicksMax: number;
  wanderStepTicksMax: number;
  yawTurnSpeed: number;
  lookAroundSpeed: number;
  lookAroundChangeTicks: number;
  wanderSpeedMultMin: number;
  wanderSpeedMultMax: number;
  wanderWaypointCountMin: number;
  wanderWaypointCountMax: number;
  wanderDecelTicks: number;
  interactionRange: number;
  updateRange: number;
  maxSpawnAttemptsPerTick: number;
  jumpVelocity: number;
  npcZoneSize: number;
  npcVisibilityCheckIntervalTicks: number;
  gameDayDurationSeconds: number;
}

export const TUNING_FIELDS: { key: keyof NpcTuning; labelKey: TranslationKey; step: number }[] = [
  { key: "wanderPauseTicksMin", labelKey: "sim.tuning.wanderPauseTicksMin", step: 1 },
  { key: "wanderPauseTicksMax", labelKey: "sim.tuning.wanderPauseTicksMax", step: 1 },
  { key: "wanderStepTicksMax", labelKey: "sim.tuning.wanderStepTicksMax", step: 1 },
  { key: "wanderDecelTicks", labelKey: "sim.tuning.wanderDecelTicks", step: 1 },
  { key: "wanderSpeedMultMin", labelKey: "sim.tuning.wanderSpeedMultMin", step: 0.05 },
  { key: "wanderSpeedMultMax", labelKey: "sim.tuning.wanderSpeedMultMax", step: 0.05 },
  { key: "wanderWaypointCountMin", labelKey: "sim.tuning.wanderWaypointCountMin", step: 1 },
  { key: "wanderWaypointCountMax", labelKey: "sim.tuning.wanderWaypointCountMax", step: 1 },
  { key: "yawTurnSpeed", labelKey: "sim.tuning.yawTurnSpeed", step: 0.01 },
  { key: "lookAroundSpeed", labelKey: "sim.tuning.lookAroundSpeed", step: 0.01 },
  { key: "lookAroundChangeTicks", labelKey: "sim.tuning.lookAroundChangeTicks", step: 1 },
  { key: "jumpVelocity", labelKey: "sim.tuning.jumpVelocity", step: 0.5 },
  { key: "interactionRange", labelKey: "sim.tuning.interactionRange", step: 0.5 },
  { key: "updateRange", labelKey: "sim.tuning.updateRange", step: 4 },
  { key: "maxSpawnAttemptsPerTick", labelKey: "sim.tuning.maxSpawnAttemptsPerTick", step: 1 },
  { key: "npcZoneSize", labelKey: "sim.tuning.npcZoneSize", step: 16 },
  { key: "npcVisibilityCheckIntervalTicks", labelKey: "sim.tuning.npcVisibilityCheckIntervalTicks", step: 1 },
  { key: "gameDayDurationSeconds", labelKey: "sim.tuning.gameDayDurationSeconds", step: 10 },
];

export interface SimSpawn {
  type: string;
  count: number;
  level?: number | null;
}

export interface SimPlayerSpec {
  name: string;
  x: number;
  z: number;
}

export interface SimViewport {
  minX: number;
  minZ: number;
  maxX: number;
  maxZ: number;
}

export interface SimulationConfig {
  halfSize: number;
  groundY: number;
  wallHeight: number;
  ticksPerSecond: number;
  seed: number;
  zoneLevel: number;
  maxNpcs: number;
  /** Hard ceiling on the population, births included. 0 = no cap. */
  populationCap: number;
  /** Most NPCs sent per frame; the rest are dropped from the payload. */
  maxNpcsPerFrame: number;
  /** Share of ground cells carrying grazing food (FLOWER/WEED). */
  vegetationDensity: number;
  gameDayDurationSeconds: number;
  /** Game days to run before pausing. 0 = until stopped. */
  maxGameDays: number;
  npcTuning: NpcTuning;
  npcDefinitionOverrides: Record<string, unknown>;
  initialSpawns: SimSpawn[];
  players: SimPlayerSpec[];
  autoSpawnEnabled: boolean;
}

/** Default ceiling on an arena's population; reproduction is exponential without one. */
export const DEFAULT_POPULATION_CAP = 1000;

/**
 * Ceiling on how many NPCs one frame may carry, whatever the population cap is set to. Above this the
 * payload itself becomes the problem, so a very large arena is the one case still clipped on screen.
 */
export const MAX_NPCS_PER_FRAME_CEILING = 2000;

/**
 * Per-frame NPC cap to pair with a population cap.
 *
 * Follows the cap the operator chose: a frame cap below the population cap means a full arena viewed
 * whole is *always* reported as partially displayed, which reads as a bug rather than as the
 * protection it is. An uncapped arena (0) falls back to the ceiling — "unlimited" is exactly the case
 * that must not be allowed to flood the socket.
 */
export function frameCapFor(populationCap: number): number {
  if (populationCap <= 0) return MAX_NPCS_PER_FRAME_CEILING;
  return Math.min(populationCap, MAX_NPCS_PER_FRAME_CEILING);
}

export interface SimulationInfo {
  id: string;
  name: string;
  halfSize: number;
  viewers: number;
  startedAtMs: number;
  tick: number;
  gameDay: number;
  npcCount: number;
  populationCap: number;
  configuredTps: number;
  realTps: number;
  paused: boolean;
}

export interface SimArena {
  halfSize: number;
  groundY: number;
  wallHeight: number;
}

export interface SimNpc {
  id: string;
  name: string;
  type: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
  currentHp: number;
  maxHp: number;
  level: number;
  isDead: boolean;
  aggroTargetId?: string | null;
  packId?: string | null;
  npcTargetId?: string | null;
  gender?: string | null;
  hunger?: number | null;
  gestationRemainingDays?: number | null;
  ageGameDays?: number | null;
}

export interface SimPlayer {
  id: string;
  name: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
}

export interface SimStats {
  tick: number;
  gameDay: number;
  configuredTps: number;
  realTps: number;
  npcCount: number;
  paused: boolean;
  foodBlocks: number;
  regrowingCells: number;
  populationCap: number;
}

export type SimEventType =
  | "SPAWN"
  | "DESPAWN"
  | "ATTACK"
  | "DAMAGE"
  | "DEATH"
  | "AGE_DEATH"
  | "AGGRO_GAIN"
  | "AGGRO_LOST"
  | "HUNGRY"
  | "FED"
  | "MATING"
  | "GESTATION_START"
  | "BIRTH"
  | "EVOLVE"
  | "PACK_CALL"
  | "PACK_JOIN"
  | "PACK_ENGAGE"
  | "PACK_DISBAND"
  | "SYSTEM";

export interface SimEvent {
  seq: number;
  tick: number;
  gameDay: number;
  type: SimEventType;
  message: string;
  npcId?: string | null;
  npcName?: string | null;
  npcType?: string | null;
  otherId?: string | null;
  otherName?: string | null;
  value?: number | null;
}

export interface SimNpcDetail {
  npc: SimNpc;
  behaviorKey: string;
  aggroMode: string;
  characterClass: string;
  xp: number;
  currentMana: number;
  maxMana: number;
  width: number;
  height: number;
  wanderSpeed: number;
  wanderRadius: number;
  aggroRange: number;
  attacks: string[];
  spells: string[];
  baseStats?: Record<string, number> | null;
  wanderPhase: string;
  spawnX: number;
  spawnZ: number;
  parentIds: string[];
  preyTargetId?: string | null;
  mateTargetId?: string | null;
  packSize?: number | null;
  packEngaged?: boolean | null;
  diet?: string | null;
  activeEffects: string[];
}

/**
 * One slice of arena history. Counters are sums over the slice; `aliveByType` is a gauge — the
 * population as last sampled inside it.
 */
export interface SimMetricBucket {
  index: number;
  startGameDay: number;
  tick: number;
  deathsByType: Record<string, number>;
  aliveByType: Record<string, number>;
  attacks: number;
  gestations: number;
  births: number;
  matings: number;
  spawns: number;
  fed: number;
  hungry: number;
  evolutions: number;
}

export interface SimMetrics {
  /** Width of one bucket, in game days. */
  bucketGameDays: number;
  buckets: SimMetricBucket[];
}

export interface SimulationDefaults {
  tuning: NpcTuning;
  npcTypes: string[];
  liveSimulations: number;
}

export type SimMessage =
  | {
      t: "snapshot";
      simulationId: string;
      arena: SimArena;
      config: SimulationConfig;
      npcs: SimNpc[];
      players: SimPlayer[];
      stats: SimStats;
      events: SimEvent[];
      truncated?: boolean;
      food?: number[];
      foodVersion?: number;
      metrics?: SimMetrics | null;
    }
  | {
      t: "frame";
      npcs: SimNpc[];
      players: SimPlayer[];
      stats: SimStats;
      events: SimEvent[];
      truncated?: boolean;
      /** Absent when unchanged since the last frame. */
      food?: number[] | null;
      foodVersion?: number;
      /** Absent on most frames: the charts refresh far slower than the arena moves. */
      metrics?: SimMetrics | null;
    }
  | { t: "simulations"; simulations: SimulationInfo[]; attachedId?: string | null }
  | { t: "npcDetail"; detail: SimNpcDetail }
  | { t: "stopped" }
  | { t: "error"; message: string };

export const EVENT_HISTORY = 300;

/** Colour per event type, shared by the log and the arena markers. */
export const EVENT_COLORS: Record<SimEventType, string> = {
  SPAWN: "#38BDF8",
  DESPAWN: "#64748B",
  ATTACK: "#FB923C",
  DAMAGE: "#F87171",
  DEATH: "#EF4444",
  AGE_DEATH: "#A78BFA",
  AGGRO_GAIN: "#F59E0B",
  AGGRO_LOST: "#94A3B8",
  HUNGRY: "#FACC15",
  FED: "#4ADE80",
  MATING: "#F472B6",
  GESTATION_START: "#E879F9",
  BIRTH: "#22D3EE",
  EVOLVE: "#818CF8",
  PACK_CALL: "#F97316",
  PACK_JOIN: "#FDBA74",
  PACK_ENGAGE: "#DC2626",
  PACK_DISBAND: "#9CA3AF",
  SYSTEM: "#8A99AF",
};

export const EVENT_LABEL_KEYS: Record<SimEventType, TranslationKey> = {
  SPAWN: "sim.event.SPAWN",
  DESPAWN: "sim.event.DESPAWN",
  ATTACK: "sim.event.ATTACK",
  DAMAGE: "sim.event.DAMAGE",
  DEATH: "sim.event.DEATH",
  AGE_DEATH: "sim.event.AGE_DEATH",
  AGGRO_GAIN: "sim.event.AGGRO_GAIN",
  AGGRO_LOST: "sim.event.AGGRO_LOST",
  HUNGRY: "sim.event.HUNGRY",
  FED: "sim.event.FED",
  MATING: "sim.event.MATING",
  GESTATION_START: "sim.event.GESTATION_START",
  BIRTH: "sim.event.BIRTH",
  EVOLVE: "sim.event.EVOLVE",
  PACK_CALL: "sim.event.PACK_CALL",
  PACK_JOIN: "sim.event.PACK_JOIN",
  PACK_ENGAGE: "sim.event.PACK_ENGAGE",
  PACK_DISBAND: "sim.event.PACK_DISBAND",
  SYSTEM: "sim.event.SYSTEM",
};

/** Sentinel for "no NPC-type filter" in the event log. */
export const ALL_NPC_TYPES = "*";

/**
 * Rows the event log should show. [npcType] is [ALL_NPC_TYPES] or a concrete type; filtering by a
 * type necessarily hides rows that belong to no NPC (system messages). With [newestFirst] the most
 * recent row comes first, which is how the log is read.
 */
export function filterEvents(
  events: readonly SimEvent[],
  activeTypes: ReadonlySet<SimEventType>,
  npcType: string = ALL_NPC_TYPES,
  newestFirst: boolean = false,
): SimEvent[] {
  const kept = events.filter(
    (event) => activeTypes.has(event.type) && (npcType === ALL_NPC_TYPES || event.npcType === npcType),
  );
  // filter() already returned a fresh array, so reversing it leaves the caller's list alone
  return newestFirst ? kept.reverse() : kept;
}

/** NPC types appearing in [events], sorted, for the filter dropdown. */
export function npcTypesInEvents(events: readonly SimEvent[]): string[] {
  const types = new Set<string>();
  for (const event of events) if (event.npcType) types.add(event.npcType);
  return [...types].sort();
}

/**
 * Categorical palette for NPC types, 50 slots.
 *
 * Generated on an OKLCH grid — four lightness tiers inside L 0.48–0.67, 24 hues
 * each, thinned by farthest-point sampling — then ordered so that consecutive
 * slots are as far apart as perception allows. Checked against the chart surface
 * `#0E1726`: lightness band, chroma floor and 3:1 contrast all pass, and on the
 * adjacent pairlist colour-blind ΔE is 13.3 (target ≥ 8) with normal-vision ΔE
 * 25.2 (floor ≥ 15).
 *
 * *Consecutive* slots are the guarantee, not every pair: fifty colours no
 * dichromat can confuse do not exist — past a dozen, any palette has pairs that
 * read alike. It holds where it is needed because segments that touch in a stack
 * are palette-neighbours, which is what [npcColorSlot] and `stackKeys` are for.
 */
const NPC_PALETTE = [
  "#00977a",
  "#9d60cf",
  "#968100",
  "#6b72e4",
  "#e65e78",
  "#477ce6",
  "#bf3e22",
  "#00a6b5",
  "#bf3948",
  "#008dbf",
  "#d44d50",
  "#0077b9",
  "#e86158",
  "#7059cc",
  "#679200",
  "#be52a5",
  "#359930",
  "#af58bc",
  "#318604",
  "#d562b1",
  "#777500",
  "#e05e95",
  "#5463d2",
  "#b56f00",
  "#8382f7",
  "#008565",
  "#c668c9",
  "#6ba61f",
  "#b43c81",
  "#a59400",
  "#bc3966",
  "#009ede",
  "#946700",
  "#b470de",
  "#009a5e",
  "#9949ae",
  "#387100",
  "#ca4d8b",
  "#0086db",
  "#d14b6f",
  "#2f95f6",
  "#a84199",
  "#39ad4d",
  "#8769dc",
  "#d2532a",
  "#296dd1",
  "#da7300",
  "#8751c0",
  "#00ab90",
  "#b25000",
] as const;

/** Palette slot handed to each type, in the order the types were first seen. */
const npcSlots = new Map<string, number>();

/**
 * Stable colour per NPC type, shared by the arena markers, the charts and the
 * legends.
 *
 * Slots are handed out in first-seen order rather than hashed from the name: the
 * palette only promises that *neighbouring* slots are easy to tell apart, so the
 * types on screen have to sit next to each other in it. A hash would scatter
 * them across all fifty and hand back pairs that look the same. The cost is that
 * a type's colour depends on the order the arena revealed it, which is why every
 * chart carries a legend.
 */
export function npcColor(type: string): string {
  return NPC_PALETTE[npcColorSlot(type)];
}

/** Palette position of a type, claiming the next free slot the first time. */
export function npcColorSlot(type: string): number {
  const known = npcSlots.get(type);
  if (known !== undefined) return known;
  // past fifty types the palette wraps and colours repeat — a legend still tells
  // them apart, and no arena comes close to that many
  const slot = npcSlots.size % NPC_PALETTE.length;
  npcSlots.set(type, slot);
  return slot;
}

/** Drop every assignment. Tests only — the app wants one stable map per session. */
export function resetNpcColors() {
  npcSlots.clear();
}
