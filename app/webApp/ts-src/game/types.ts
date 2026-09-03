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
  fpsMin: number;
  fpsMax: number;
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
  fullMeshedChunks: number;
  impostorMeshedChunks: number;
  zoneLevel: number;
  meshDrainMsAvg: number;
  meshDrainMsMin: number;
  meshDrainMsMax: number;
  gpuUploadMsAvg: number;
  gpuUploadMsMin: number;
  gpuUploadMsMax: number;
  wsDecodeMsAvg: number;
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

export interface ShopItemData {
  itemType: string;
  buyPrice: number;
  sellPrice: number;
}

export interface NpcDialogData {
  type: string;
  name: string;
  npcId?: string;
  shopItems?: ShopItemData[];
}

export interface ActionBlockHudData {
  name: string;
  values: Record<string, string>;
}

export interface ActionBlockFormData {
  pos: { x: number; y: number; z: number };
  name: string;
  onActivate: string;
  onTargetEvent: string;
  onRemoteEvent: string;
  variables: Record<string, string>;
  error?: string | null;
}

export interface MailData {
  id: string;
  from: string;
  to: string;
  subject: string;
  body: string;
  attachments?: Record<string, number>;
  copperAmount?: number;
  sentAt: number;
  seen: boolean;
  attachmentsClaimed: boolean;
}

export interface AuctionBidData {
  bidderId: string;
  bidderName: string;
  amount: number;
  atMs: number;
}

export interface AuctionFilter {
  itemType: string | null;
  sellerName: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  mineOnly: boolean;
  expiredOnly: boolean;
  myBidsOnly: boolean;
}

export interface AuctionData {
  id: string;
  sellerId: string;
  sellerName: string;
  itemType: string;
  quantity: number;
  createdAtMs: number;
  expiresAtMs: number;
  duration: "H12" | "H24" | "H48" | "H96";
  startingPrice: number;
  buyNowPrice: number | null;
  currentBid: number | null;
  currentBidderId: string | null;
  currentBidderName: string | null;
  status: "ACTIVE" | "SOLD" | "EXPIRED" | "CANCELLED";
  bidHistory: AuctionBidData[];
}

export interface ClaimData {
  id: string;
  chunks: { cx: number; cz: number }[];
  yMin: number;
  yMax: number;
  ownerId: string;
  ownerName: string;
  trustedPlayerNames: string[];
}

export type GuildPermission =
  | "INVITE"
  | "KICK"
  | "MANAGE_RANKS"
  | "EDIT_MOTD"
  | "BANK_DEPOSIT"
  | "BANK_WITHDRAW"
  | "DISBAND"
  | "EDIT_INFO";

export interface GuildRank {
  name: string;
  order: number;
  flags: GuildPermission[];
}

export interface GuildMemberInfo {
  playerId: string;
  playerName: string;
  rank: string;
  joinedAtMs: number;
  online: boolean;
}

export interface GuildBankEntryInfo {
  playerName: string;
  itemId: string;
  delta: number;
  atMs: number;
}

export interface GuildInfo {
  id: string;
  name: string;
  tag: string;
  motd: string;
  createdAtMs: number;
  ownerId: string;
  ranks: GuildRank[];
  members: GuildMemberInfo[];
  bank: Record<string, number>;
  bankLog: GuildBankEntryInfo[];
  myRank: string;
  myFlags: GuildPermission[];
}

export interface GroupMemberInfo {
  playerId: string;
  playerName: string;
  online: boolean;
}

export interface GroupInfo {
  id: string;
  leaderId: string;
  leaderName: string;
  members: GroupMemberInfo[];
}

export interface PetInfo {
  id: string;
  name: string;
  npcType: string;
  level: number;
  xp: number;
  currentHp: number;
  maxHp: number;
  spawned: boolean;
  dead: boolean;
  resurrectReadyAtMs: number;
}

export interface PetRosterData {
  pets: PetInfo[];
  activePetId: string | null;
}

export interface FactionDefinition {
  id: string;
  name: string;
  color: string;
  description: string;
}

export interface FactionState {
  id: string;
  memberCount: number;
}

export interface FactionSyncData {
  enabled: boolean;
  definitions: FactionDefinition[];
  states: FactionState[];
  myFactionId: string | null;
  changeCooldownRemainingMs: number;
}

export interface SocialInvite {
  kind: "group" | "guild";
  id: string;
  name: string;
  from: string;
}

export interface InstanceZoneData {
  id: string;
  name: string;
  yMin: number;
  yMax: number;
  chunks: { cx: number; cz: number }[];
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
  inventorySortA?: string;
  inventorySortB?: string;
  shadowAngleDeg?: number;
  overrideViewRadius?: number | null;
  overrideForwardViewRadius?: number | null;
  overrideUseImpostor?: boolean | null;
  overrideImpostorRadiusChunks?: number | null;
  overrideImpostorFovBonusChunks?: number | null;
  continuousBreak: boolean;
  dominantHand: "LEFT" | "RIGHT";
  disabledViewModes: string[];
  turnSpeedHorizontal: number;
  turnSpeedVertical: number;
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
  godMode: boolean;
};

export interface ItemMetaEntry {
  label: string;
  bg: string;
  healthRestore?: number;
  manaRestore?: number;
  consumable?: boolean;
  plainColor?: string | null;
}

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
  aoeRadius: number;
};

export type PreferencesSaveData = Omit<PreferencesData, "knownChannels" | "commands" | "defaultKeybindings">;
