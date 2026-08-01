// Mirrors of the Kotlin @Serializable DTOs in
// server/src/main/kotlin/org/micoli/micraft/simulation/SimulationProtocol.kt

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

export const TUNING_FIELDS: { key: keyof NpcTuning; label: string; step: number }[] = [
  { key: "wanderPauseTicksMin", label: "Pause errance min (ticks)", step: 1 },
  { key: "wanderPauseTicksMax", label: "Pause errance max (ticks)", step: 1 },
  { key: "wanderStepTicksMax", label: "Durée max d'un pas (ticks)", step: 1 },
  { key: "wanderDecelTicks", label: "Décélération (ticks)", step: 1 },
  { key: "wanderSpeedMultMin", label: "Multiplicateur vitesse min", step: 0.05 },
  { key: "wanderSpeedMultMax", label: "Multiplicateur vitesse max", step: 0.05 },
  { key: "wanderWaypointCountMin", label: "Waypoints min", step: 1 },
  { key: "wanderWaypointCountMax", label: "Waypoints max", step: 1 },
  { key: "yawTurnSpeed", label: "Vitesse de rotation", step: 0.01 },
  { key: "lookAroundSpeed", label: "Vitesse du regard", step: 0.01 },
  { key: "lookAroundChangeTicks", label: "Changement de regard (ticks)", step: 1 },
  { key: "jumpVelocity", label: "Vitesse de saut", step: 0.5 },
  { key: "interactionRange", label: "Portée d'interaction", step: 0.5 },
  { key: "updateRange", label: "Portée de mise à jour", step: 4 },
  { key: "maxSpawnAttemptsPerTick", label: "Tentatives de spawn / tick", step: 1 },
  { key: "npcZoneSize", label: "Taille de zone (blocs)", step: 16 },
  { key: "npcVisibilityCheckIntervalTicks", label: "Contrôle visibilité (ticks)", step: 1 },
  { key: "gameDayDurationSeconds", label: "Durée d'un jour (s)", step: 10 },
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
  SYSTEM: "#8A99AF",
};

export const EVENT_LABELS: Record<SimEventType, string> = {
  SPAWN: "apparition",
  DESPAWN: "disparition",
  ATTACK: "attaque",
  DAMAGE: "dégâts",
  DEATH: "mort",
  AGE_DEATH: "vieillesse",
  AGGRO_GAIN: "colère",
  AGGRO_LOST: "calme",
  HUNGRY: "faim",
  FED: "satiété",
  MATING: "accouplement",
  GESTATION_START: "gestation",
  BIRTH: "naissance",
  EVOLVE: "évolution",
  SYSTEM: "système",
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

/** Stable colour per NPC type so the arena stays readable. */
export function npcColor(type: string): string {
  let hash = 0;
  for (let i = 0; i < type.length; i++) hash = (hash * 31 + type.charCodeAt(i)) | 0;
  const hue = Math.abs(hash) % 360;
  return `hsl(${hue} 70% 60%)`;
}
