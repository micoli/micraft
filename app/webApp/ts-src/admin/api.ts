import type { ClipAxis, ClipPlaneState } from "./pages/shared/voxelEditor/clipAxis";

export interface BlockInfoDto {
  name: string;
  hardness: number;
  solid: boolean;
  transparent: boolean;
  minimapColor: [number, number, number];
  modelElement: string;
  liquid: boolean;
  rotatable?: boolean;
  hasStuds?: boolean;
  brickSize?: [number, number, number];
  heightFraction?: number;
  plainColorable?: boolean;
  isCubic?: boolean;
}

export interface PlainColorDto {
  name: string;
  hex: string;
}

export interface ChunkPosDto {
  cx: number;
  cz: number;
}

export interface ChunkTerrainInfoDto {
  cx: number;
  cz: number;
  colors: (string | null)[];
  avgHeight: number | null;
}

export interface InstanceZoneDto {
  id: string;
  name: string;
  yMin: number;
  yMax: number;
  chunks: ChunkPosDto[];
  ownerName: string;
  createdAt: number;
  enabled: boolean;
  clipPlanes: Record<ClipAxis, ClipPlaneState>;
  shortcutBarPages: (string | null)[][];
}

export interface InstanceBlockDto {
  x: number;
  y: number;
  z: number;
  type: string;
  state: number;
  xOffset?: number;
  zOffset?: number;
}

export interface SceneDto {
  id: string;
  name: string;
  width: number;
  height: number;
  depth: number;
  ownerName: string;
  createdAt: number;
  shortcutBarPages: (string | null)[][];
}

export interface SceneBlockDto {
  x: number;
  y: number;
  z: number;
  type: string;
  state: number;
}

export interface NpcTypeDto {
  bbmodelFile: string;
  behaviorKey: string;
  width: number;
  height: number;
  wanderSpeed: number;
  autoSpawn: boolean;
}

export interface ItemDto {
  buildable: boolean;
  placesBlock: string | null;
}

export interface StatusSnapshot {
  connectedPlayers: number;
  playerNames: string[];
  npcTotal: number;
  npcByType: Record<string, number>;
  npcEstBytes: number;
  worldItems: number;
  loadedChunks: number;
  gameTicks: number;
  networkBytesIn: number;
  networkBytesOut: number;
  activeLiquids: number;
  pendingLiquidTicks: number;
  liquidEstBytes: number;
  activeVegetation: number;
  vegetationEstBytes: number;
  heapUsedMb: number;
  heapMaxMb: number;
  nonHeapUsedMb: number;
  processors: number;
  ticksPerDay: number;
}

export interface ClassAttackAccess {
  attack: string;
  level: number;
}

export interface ClassLevelEntry {
  attacks: ClassAttackAccess[];
  spells: string[];
}

export interface ClassDefinitionEntry {
  strBonus: number;
  dexBonus: number;
  intelBonus: number;
  wisBonus: number;
  conBonus: number;
  chaBonus: number;
  classResource: string;
  hpFormula: string;
  manaFormula: string;
  rageFormula: string;
  levels: Record<string, ClassLevelEntry>;
}

export interface NpcAdminDto {
  id: string;
  name: string;
  type: string;
  level: number;
  xp: number;
  gender: string | null;
  currentHp: number;
  maxHp: number;
  isDead: boolean;
  aggroMode: string;
  tier: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
  zone: string;
  parentIds: string[];
  skills: string[];
  ageGameDays: number | null;
  hunger: number | null;
  gestationRemainingDays: number | null;
  lastReproductionDay: number | null;
  motherLevel: number | null;
  animalStats: { str: number; dex: number; intel: number; wis: number; con: number; cha: number } | null;
}

export interface WorldStatsDto {
  name: string;
  seed: number;
  generator: string;
  createdAt: string;
  chunkCount: number;
  playerCount: number;
  isActive: boolean;
}

export interface UserDto {
  email: string;
  displayName: string;
  groups: string[];
}

export interface BaseStats {
  str: number;
  dex: number;
  intel: number;
  wis: number;
  con: number;
  cha: number;
}

export interface CharacterData {
  id: string;
  name: string;
  characterClass: string;
  level: number;
  xp: number;
  baseStats: BaseStats;
  currentHp: number;
  currentMana: number;
  currentRage: number;
  currentTokens: number;
}

export interface PlayerState {
  id: string;
  name: string;
  skin: string;
  language: string;
  fieldOfView: number;
  shadersEnabled: boolean;
  animatedFavicon: boolean;
  godMode: boolean;
  lightBoostEnabled: boolean;
  rpgOptOut: boolean;
  characterData: CharacterData | null;
  email?: string;
}

export interface PlayerFile {
  state: PlayerState;
  keybindings: Record<string, string[]>;
  customCommands: Record<string, string[]>;
}

async function request(method: string, path: string, body?: unknown): Promise<Response> {
  return fetch(path, {
    method,
    headers: body !== undefined ? { "Content-Type": "application/json" } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

const get = (path: string) => request("GET", path);
const post = (path: string, body?: unknown) => request("POST", path, body);
const put = (path: string, body: unknown) => request("PUT", path, body);
const del = (path: string) => request("DELETE", path);

export const api = {
  status: {
    get: () => get("/api/admin/status").then((r) => r.json() as Promise<StatusSnapshot>),
    restart: () => post("/api/admin/restart"),
    setGameTime: (hour: number, minute: number) => put("/api/admin/gametime", { hour, minute }),
  },
  users: {
    list: () => get("/api/admin/users").then((r) => r.json() as Promise<UserDto[]>),
    create: (u: { email: string; password: string; displayName: string; groups: string[] }) =>
      post("/api/admin/users", u),
    update: (email: string, u: { displayName?: string; groups?: string[] }) =>
      put(`/api/admin/users/${encodeURIComponent(email)}`, u),
    delete: (email: string) => del(`/api/admin/users/${encodeURIComponent(email)}`),
  },
  players: {
    list: () => get("/api/admin/players").then((r) => r.json() as Promise<string[]>),
    get: (name: string) =>
      get(`/api/admin/players/${encodeURIComponent(name)}`).then((r) => r.json() as Promise<PlayerFile>),
    saveKeybindings: (name: string, kb: Record<string, string[]>) =>
      put(`/api/admin/players/${encodeURIComponent(name)}/keybindings`, kb),
    savePreferences: (name: string, prefs: Partial<PlayerState>) =>
      put(`/api/admin/players/${encodeURIComponent(name)}/preferences`, prefs),
    saveRpg: (name: string, rpg: { characterClass: string } & Partial<BaseStats>) =>
      put(`/api/admin/players/${encodeURIComponent(name)}/rpg`, rpg),
    rename: (name: string, newName: string) =>
      post(`/api/admin/players/${encodeURIComponent(name)}/rename`, { newName }),
  },
  worlds: {
    list: () => get("/api/admin/worlds").then((r) => r.json() as Promise<WorldStatsDto[]>),
    create: (name: string, seed: number) => post("/api/admin/worlds", { name, seed }),
  },
  npcs: {
    list: () => get("/api/admin/npcs").then((r) => r.json() as Promise<NpcAdminDto[]>),
  },
  classes: {
    get: () => get("/api/admin/classes").then((r) => r.json() as Promise<Record<string, ClassDefinitionEntry>>),
    skills: () => get("/api/admin/skills").then((r) => r.json() as Promise<{ attacks: string[]; spells: string[] }>),
  },
  skins: {
    list: () => get("/api/skins").then((r) => r.json() as Promise<string[]>),
    bbmodel: (skin: string) =>
      fetch(`/api/models/skins/${encodeURIComponent(skin)}/${encodeURIComponent(skin)}.bbmodel`).then(
        (r) => r.json() as Promise<BbModel>,
      ),
  },
  blocks: {
    list: () => get("/api/admin/blocks").then((r) => r.json() as Promise<BlockInfoDto[]>),
  },
  plainColors: {
    list: () => get("/api/admin/plain-colors").then((r) => r.json() as Promise<PlainColorDto[]>),
  },
  chunks: {
    discovered: () => get("/api/admin/chunks/discovered").then((r) => r.json() as Promise<ChunkPosDto[]>),
  },
  terrain: {
    list: () => get("/api/map/terrain").then((r) => r.json() as Promise<ChunkTerrainInfoDto[]>),
  },
  instances: {
    list: () => get("/api/admin/instances").then((r) => r.json() as Promise<InstanceZoneDto[]>),
    get: (id: string) =>
      get(`/api/admin/instances/${encodeURIComponent(id)}`).then((r) => r.json() as Promise<InstanceZoneDto>),
    create: (data: { name: string; yMin: number; yMax: number; chunks: ChunkPosDto[] }) =>
      post("/api/admin/instances", data).then((r) => r.json() as Promise<InstanceZoneDto>),
    rename: (id: string, name: string) => put(`/api/admin/instances/${encodeURIComponent(id)}`, { name }),
    updateBounds: (id: string, yMin: number, yMax: number) =>
      put(`/api/admin/instances/${encodeURIComponent(id)}/bounds`, { yMin, yMax }).then(
        (r) => r.json() as Promise<InstanceZoneDto>,
      ),
    updateChunks: (id: string, chunks: ChunkPosDto[]) =>
      put(`/api/admin/instances/${encodeURIComponent(id)}/chunks`, { chunks }).then(
        (r) => r.json() as Promise<InstanceZoneDto>,
      ),
    setEnabled: (id: string, enabled: boolean) =>
      put(`/api/admin/instances/${encodeURIComponent(id)}/enabled`, { enabled }).then(
        (r) => r.json() as Promise<InstanceZoneDto>,
      ),
    updateLayout: (id: string, clipPlanes: Record<ClipAxis, ClipPlaneState>, shortcutBarPages: (string | null)[][]) =>
      put(`/api/admin/instances/${encodeURIComponent(id)}/layout`, { clipPlanes, shortcutBarPages }).then(
        (r) => r.json() as Promise<InstanceZoneDto>,
      ),
    delete: (id: string) => del(`/api/admin/instances/${encodeURIComponent(id)}`),
  },
  scenes: {
    list: () => get("/api/admin/scenes").then((r) => r.json() as Promise<SceneDto[]>),
    get: (id: string) => get(`/api/admin/scenes/${encodeURIComponent(id)}`).then((r) => r.json() as Promise<SceneDto>),
    create: (name: string, width: number, height: number, depth: number) =>
      post("/api/admin/scenes", { name, width, height, depth }).then((r) => r.json() as Promise<SceneDto>),
    rename: (id: string, name: string) => put(`/api/admin/scenes/${encodeURIComponent(id)}`, { name }),
    resize: (id: string, width: number, height: number, depth: number) =>
      put(`/api/admin/scenes/${encodeURIComponent(id)}/dimensions`, { width, height, depth }).then(
        (r) => r.json() as Promise<SceneDto>,
      ),
    delete: (id: string) => del(`/api/admin/scenes/${encodeURIComponent(id)}`),
    updateLayout: (id: string, shortcutBarPages: (string | null)[][]) =>
      put(`/api/admin/scenes/${encodeURIComponent(id)}/layout`, { shortcutBarPages }).then(
        (r) => r.json() as Promise<SceneDto>,
      ),
    // Binary layout: [width:i32be][height:i32be][depth:i32be][blocks: width*height*depth bytes][states: same length]
    getBlocksRaw: (id: string) =>
      fetch(`/api/admin/scenes/${encodeURIComponent(id)}/blocks/raw`).then((r) => r.arrayBuffer()),
  },
  npcTypes: {
    list: () => get("/api/admin/npc-types").then((r) => r.json() as Promise<Record<string, NpcTypeDto>>),
  },
  items: {
    list: () => get("/api/admin/items").then((r) => r.json() as Promise<Record<string, ItemDto>>),
  },
  configs: {
    list: () => get("/api/admin/configs").then((r) => r.json() as Promise<string[]>),
    get: (filename: string) => get(`/api/admin/configs/${filename}`).then((r) => r.text()),
    save: (filename: string, content: string) =>
      fetch(`/api/admin/configs/${filename}`, {
        method: "PUT",
        headers: { "Content-Type": "text/plain" },
        body: content,
      }),
    schema: (filename: string) => get(`/api/admin/schemas/${filename}`).then((r) => r.json()),
  },
};
