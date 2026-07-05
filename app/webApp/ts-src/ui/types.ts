export interface HudData {
  x: number;
  y: number;
  z: number;
  yaw: number;
  pitch: number;
  stance: string;
  speed: number;
  fps: number;
  kbIn: number;
  kbOut: number;
  biome: string;
  targetBlock: string;
  gameTime: string;
  reconcileXzStats: string;
  reconcileYStats: string;
  tickDtMs: number;
  tickJitterMs: number;
  tickDtMinMs: number;
  tickDtMaxMs: number;
  tickJitterMinMs: number;
  tickJitterMaxMs: number;
  chunkDownloading: number;
  chunkMeshing: number;
}

export interface LogEntry {
  time: string;
  msg: string;
  channel: string;
  sender?: string;
}

export type HudMode = "simple" | "medium" | "complete";

export interface LayoutWidget {
  type: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface GameLayout {
  name: string;
  widgets: LayoutWidget[];
}

export interface NpcDialogData {
  type: string;
  name: string;
}

export interface CommandInfo {
  id: string;
  command: string;
  description: string;
  autocompleteArgs?: number[];
}

export interface RecipeIngredient {
  type: string;
  count: number;
}

export interface RecipeDefinition {
  giveType: string;
  giveId: string;
  giveAmount: number;
  ingredients: RecipeIngredient[];
}

export interface BaseStats {
  str: number;
  dex: number;
  intel: number;
  wis: number;
  con: number;
  cha: number;
}

export interface DerivedStats {
  maxHp: number;
  maxMana: number;
  meleeDmg: number;
  rangedDmg: number;
  spellDmg: number;
  critChancePct: number;
  critDmgMult: number;
  dodgePct: number;
  magicResistPct: number;
  initiative: number;
  hpRegenPerSec: number;
  manaRegenPerSec: number;
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
}

export interface CharacterSyncData {
  character: CharacterData;
  derived: DerivedStats;
}

export interface PreferencesData {
  subscribedChannels: string[];
  knownChannels: string[];
  disabledCommands: string[];
  shadersEnabled: boolean;
  animatedFavicon: boolean;
  chunkDebugVisible: boolean;
  commands: CommandInfo[];
  keybindings: Record<string, string[]>;
  customCommands: Record<string, string[]>;
}
