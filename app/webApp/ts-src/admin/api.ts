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
  gender: string | null;
  currentHp: number;
  maxHp: number;
  isDead: boolean;
  aggroMode: string;
  tier: string;
  x: number;
  y: number;
  z: number;
  zone: string;
  parentIds: string[];
  skills: string[];
  ageGameDays: number | null;
  hunger: number | null;
  gestationRemainingDays: number | null;
  lastReproductionDay: number | null;
  motherLevel: number | null;
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
