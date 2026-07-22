export interface ChannelSubscription {
  name: string;
  autoFocus: boolean;
}

export interface HudData {
  x: number;
  y: number;
  z: number;
  yaw: number;
  pitch: number;
  stance: string;
  weather: string;
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
  zoneLevel: number;
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
  effectiveBaseStats: BaseStats;
}

export interface TradeData {
  tradeId: string;
  otherPlayer: string;
  myOffer: Record<string, number>;
  theirOffer: Record<string, number>;
  myAccepted: boolean;
  theirAccepted: boolean;
}

export interface PreferencesData {
  subscribedChannels: ChannelSubscription[];
  knownChannels: string[];
  disabledCommands: string[];
  shadersEnabled: boolean;
  dynamicFogEnabled: boolean;
  animatedFavicon: boolean;
  chunkDebugVisible: boolean;
  statisticsVisible: boolean;
  attackPanelVisible: boolean;
  commands: CommandInfo[];
  keybindings: Record<string, string[]>;
  defaultKeybindings: Record<string, string[]>;
  customCommands: Record<string, string[]>;
  macros: Record<string, string>;
  macroIcons?: Record<string, string>;
  fieldOfView: number;
  autoTargetEnabled: boolean;
}

export type CombatTargetData = {
  targetId: string | null;
  displayName: string | null;
  currentHp: number;
  maxHp: number;
  targetOfTarget: { id: string; name: string; currentHp: number; maxHp: number } | null;
  distance: number | null;
  level?: number;
};

export type PlayerStatusData = {
  currentHp: number;
  maxHp: number;
  currentMana: number;
  maxMana: number;
  currentRage: number;
  maxRage: number;
  currentTokens: number;
  maxTokens: number;
  stance: string;
  globalCooldownRemainingMs: number;
  attackCooldownsRemainingMs: Record<string, number>;
};

export type ShortcutSlot =
  | { kind: "item"; id: string }
  | { kind: "attack"; id: string }
  | { kind: "macro"; id: string }
  | { kind: "spell"; id: string };

export type AttackMeta = {
  damageType: string;
  manaCost: number;
  rageCost: number;
  cooldownMs: number;
  power: number;
  weaponDice: string;
  attackId: string;
  level: number;
};

export type ClassAttackAccess = { attack: string; level: number };
export type ClassDefinitions = Record<string, Record<string, ClassAttackAccess[]>>;

export type NpcProximityEntry = { id: string; name: string; relAngle: number; dist: number; aggro: boolean };

export type QuestStatus = "TODO" | "IN_PROGRESS" | "COMPLETED" | "ABANDONED" | "FAILED";
export type QuestProgress = {
  status: QuestStatus;
  progress: Record<string, number>;
  acceptedAt: number | null;
  completedAt: number | null;
  lastCompletedAt: number | null;
};

export type SpellMeta = {
  type: string;
  rageGain: number;
  tokenCost: number;
  manaCost: number;
  rageCost: number;
  cooldownMs: number;
};

export type PreferencesSaveData = Omit<PreferencesData, "knownChannels" | "commands" | "defaultKeybindings">;
