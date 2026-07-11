import {
  ChannelSubscription,
  CharacterSyncData,
  GameLayout,
  HudData,
  HudMode,
  LogEntry,
  NpcDialogData,
  PreferencesData,
  RecipeDefinition,
} from "./types";

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

export type SpellMeta = {
  type: string;
  rageGain: number;
  tokenCost: number;
  manaCost: number;
  rageCost: number;
  cooldownMs: number;
};

export interface UiState {
  hud: HudData | null;
  hudMode: HudMode;
  notif: { msg: string; key: number } | null;
  logs: LogEntry[];
  logVisible: boolean;
  logKey: number;
  subscribedChannels: ChannelSubscription[];
  knownChannels: string[];
  activeChannel: string;
  unreadChannels: string[];
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  attackMeta: Record<string, AttackMeta>;
  spellMeta: Record<string, SpellMeta>;
  hotbarVisible: boolean;
  healthBarVisible: boolean;
  shortcutBar: (ShortcutSlot | null)[];
  selectedSlot: number;
  consoleOpen: boolean;
  disconnectMsg: string | null;
  chunkLoading: { meshed: number; downloaded: number; total: number } | null;
  layouts: GameLayout[];
  activeLayout: string;
  layoutEditorOpen: boolean;
  npcDialog: NpcDialogData | null;
  codexOpen: boolean;
  craftOpen: boolean;
  craftRecipes: Record<string, RecipeDefinition>;
  craftKnownRecipes: string[];
  preferencesOpen: boolean;
  preferences: PreferencesData | null;
  pauseMenuOpen: boolean;
  macroEditorOpen: boolean;
  characterOpen: boolean;
  characterSyncData: CharacterSyncData | null;
  biomeMapVisible: boolean;
  combatTarget: {
    targetId: string | null;
    displayName: string | null;
    currentHp: number;
    maxHp: number;
    targetOfTarget: { id: string; name: string; currentHp: number; maxHp: number } | null;
    distance: number | null;
  } | null;
  playerStatus: {
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
  } | null;
  playerDowned: boolean;
  xpState: { xpGained: number; totalXp: number; level: number; leveledUp: boolean; nextLevelXp: number } | null;
  tradeOpen: boolean;
  tradeId: string | null;
  tradeOtherPlayer: string | null;
  tradeMyOffer: Record<string, number>;
  tradeTheirOffer: Record<string, number>;
  tradeMyAccepted: boolean;
  tradeTheirAccepted: boolean;
  classDefinitions: ClassDefinitions | null;
}

export type UiAction =
  | { type: "hud"; data: HudData }
  | { type: "hud_mode_cycle" }
  | { type: "notification"; msg: string }
  | { type: "log"; msg: string; channel: string }
  | { type: "chat_message"; channel: string; sender: string; msg: string }
  | { type: "channels_sync"; subscribed: ChannelSubscription[]; known: string[] }
  | { type: "active_channel_select"; channel: string }
  | { type: "inventory"; data: Record<string, number> }
  | { type: "item_meta_loaded"; data: Record<string, { label: string; bg: string }> }
  | { type: "attack_meta_loaded"; data: Record<string, AttackMeta> }
  | { type: "spell_meta_loaded"; data: Record<string, SpellMeta> }
  | { type: "class_definitions_loaded"; data: ClassDefinitions }
  | { type: "hotbar_toggle" }
  | { type: "healthbar_toggle" }
  | { type: "shortcut_bar_update"; data: { slots: (ShortcutSlot | null)[]; selected: number } }
  | { type: "slot_select"; slot: number }
  | { type: "console_show" }
  | { type: "console_hide" }
  | { type: "disconnect_show"; message: string }
  | { type: "disconnect_hide" }
  | { type: "chunk_loading_update"; meshed: number; downloaded: number; total: number }
  | { type: "chunk_loading_hide" }
  | { type: "log_hide" }
  | { type: "layouts_sync"; layouts: GameLayout[]; activeLayout: string }
  | { type: "layout_editor_show" }
  | { type: "layout_editor_hide" }
  | { type: "layout_editor_save"; layouts: GameLayout[]; activeLayout: string }
  | { type: "npc_dialog_open"; payload: NpcDialogData }
  | { type: "npc_dialog_close" }
  | { type: "codex_open" }
  | { type: "codex_close" }
  | { type: "craft_open" }
  | { type: "craft_close" }
  | { type: "craft_sync"; recipes: Record<string, RecipeDefinition>; knownRecipes: string[] }
  | { type: "preferences_sync"; data: PreferencesData }
  | { type: "preferences_show" }
  | { type: "preferences_hide" }
  | {
      type: "preferences_save";
      subscribedChannels: ChannelSubscription[];
      disabledCommands: string[];
      shadersEnabled: boolean;
      animatedFavicon: boolean;
      chunkDebugVisible: boolean;
      keybindings: Record<string, string[]>;
      customCommands: Record<string, string[]>;
      macros?: Record<string, string>;
      fieldOfView?: number;
    }
  | { type: "pause_menu_show" }
  | { type: "pause_menu_hide" }
  | { type: "macro_editor_open" }
  | { type: "macro_editor_close" }
  | { type: "character_open" }
  | { type: "character_close" }
  | { type: "character_sync"; data: CharacterSyncData }
  | { type: "ingame_map_toggle" }
  | { type: "trade_open"; tradeId: string; otherPlayer: string }
  | {
      type: "trade_update";
      tradeId: string;
      myOffer: Record<string, number>;
      theirOffer: Record<string, number>;
      myAccepted: boolean;
      theirAccepted: boolean;
    }
  | { type: "trade_close" }
  | { type: "combat_target_update"; data: unknown }
  | { type: "health_update"; data: unknown }
  | { type: "player_status_update"; data: unknown }
  | { type: "status_effect_update"; data: unknown }
  | { type: "player_downed"; playerId: string }
  | { type: "player_respawned"; data: unknown }
  | { type: "xp_gained"; data: unknown };

const HUD_MODES: HudMode[] = ["simple", "medium", "complete"];
let notifKey = 0;
const MC_LOG_MAX = 100;

export function reducer(state: UiState, action: UiAction): UiState {
  switch (action.type) {
    case "hud":
      return { ...state, hud: action.data };
    case "hud_mode_cycle": {
      const next = HUD_MODES[(HUD_MODES.indexOf(state.hudMode) + 1) % HUD_MODES.length];
      try {
        localStorage.setItem("mc_hud_mode", next);
      } catch {
        /* ignore */
      }
      return { ...state, hudMode: next };
    }
    case "notification":
      return { ...state, notif: { msg: action.msg, key: ++notifKey } };
    case "log": {
      const now = new Date();
      const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
      const entry: LogEntry = { time, msg: action.msg, channel: action.channel };
      const logs = [...state.logs, entry].slice(-MC_LOG_MAX);
      const unreadChannels =
        action.channel !== state.activeChannel && !state.unreadChannels.includes(action.channel)
          ? [...state.unreadChannels, action.channel]
          : state.unreadChannels;
      return { ...state, logs, logVisible: true, logKey: state.logKey + 1, unreadChannels };
    }
    case "chat_message": {
      const now = new Date();
      const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
      const entry: LogEntry = { time, msg: action.msg, channel: action.channel, sender: action.sender };
      const logs = [...state.logs, entry].slice(-MC_LOG_MAX);
      const autoFocus = state.subscribedChannels.find((c) => c.name === action.channel)?.autoFocus ?? false;
      const activeChannel = autoFocus ? action.channel : state.activeChannel;
      const unreadChannels = autoFocus
        ? state.unreadChannels.filter((c) => c !== action.channel)
        : action.channel !== state.activeChannel && !state.unreadChannels.includes(action.channel)
          ? [...state.unreadChannels, action.channel]
          : state.unreadChannels;
      return { ...state, logs, logVisible: true, logKey: state.logKey + 1, unreadChannels, activeChannel };
    }
    case "channels_sync":
      return { ...state, subscribedChannels: action.subscribed, knownChannels: action.known };
    case "active_channel_select":
      return {
        ...state,
        activeChannel: action.channel,
        unreadChannels: state.unreadChannels.filter((c) => c !== action.channel),
      };
    case "log_hide":
      return { ...state, logVisible: false };
    case "inventory":
      return { ...state, inventory: action.data };
    case "item_meta_loaded":
      return { ...state, itemMeta: action.data };
    case "attack_meta_loaded":
      return { ...state, attackMeta: action.data };
    case "spell_meta_loaded":
      return { ...state, spellMeta: action.data };
    case "class_definitions_loaded":
      return { ...state, classDefinitions: action.data };
    case "hotbar_toggle":
      return { ...state, hotbarVisible: !state.hotbarVisible };
    case "healthbar_toggle":
      return { ...state, healthBarVisible: !state.healthBarVisible };
    case "shortcut_bar_update":
      return { ...state, shortcutBar: action.data.slots, selectedSlot: action.data.selected };
    case "slot_select":
      return { ...state, selectedSlot: action.slot };
    case "console_show":
      return { ...state, consoleOpen: true };
    case "console_hide":
      return { ...state, consoleOpen: false };
    case "disconnect_show":
      return { ...state, disconnectMsg: action.message };
    case "disconnect_hide":
      return { ...state, disconnectMsg: null };
    case "chunk_loading_update":
      return {
        ...state,
        chunkLoading: { meshed: action.meshed, downloaded: action.downloaded, total: action.total },
      };
    case "chunk_loading_hide":
      return { ...state, chunkLoading: null };
    case "layouts_sync":
      return { ...state, layouts: action.layouts, activeLayout: action.activeLayout };
    case "layout_editor_show":
      return { ...state, layoutEditorOpen: true };
    case "layout_editor_hide":
      return { ...state, layoutEditorOpen: false };
    case "layout_editor_save":
      return { ...state, layouts: action.layouts, activeLayout: action.activeLayout, layoutEditorOpen: false };
    case "npc_dialog_open":
      return { ...state, npcDialog: action.payload };
    case "npc_dialog_close":
      return { ...state, npcDialog: null };
    case "codex_open":
      return { ...state, codexOpen: true };
    case "codex_close":
      return { ...state, codexOpen: false };
    case "craft_open":
      return { ...state, craftOpen: true };
    case "craft_close":
      return { ...state, craftOpen: false };
    case "craft_sync":
      return { ...state, craftRecipes: action.recipes, craftKnownRecipes: action.knownRecipes };
    case "preferences_sync":
      return { ...state, preferences: action.data };
    case "preferences_show":
      return { ...state, preferencesOpen: true };
    case "preferences_hide":
      return { ...state, preferencesOpen: false };
    case "preferences_save": {
      const prefs = state.preferences
        ? {
            ...state.preferences,
            subscribedChannels: action.subscribedChannels,
            disabledCommands: action.disabledCommands,
            shadersEnabled: action.shadersEnabled,
            animatedFavicon: action.animatedFavicon,
            chunkDebugVisible: action.chunkDebugVisible,
            keybindings: action.keybindings,
            customCommands: action.customCommands,
            ...(action.macros !== undefined ? { macros: action.macros } : {}),
            ...(action.fieldOfView !== undefined ? { fieldOfView: action.fieldOfView } : {}),
          }
        : state.preferences;
      return { ...state, preferences: prefs, preferencesOpen: false };
    }
    case "pause_menu_show":
      return { ...state, pauseMenuOpen: true };
    case "pause_menu_hide":
      return { ...state, pauseMenuOpen: false };
    case "macro_editor_open":
      return { ...state, macroEditorOpen: true };
    case "macro_editor_close":
      return { ...state, macroEditorOpen: false };
    case "character_open":
      return { ...state, characterOpen: true };
    case "character_close":
      return { ...state, characterOpen: false };
    case "character_sync":
      return { ...state, characterSyncData: action.data };
    case "ingame_map_toggle":
      return { ...state, biomeMapVisible: !state.biomeMapVisible };
    case "trade_open":
      return {
        ...state,
        tradeOpen: true,
        tradeId: action.tradeId,
        tradeOtherPlayer: action.otherPlayer,
        tradeMyOffer: {},
        tradeTheirOffer: {},
        tradeMyAccepted: false,
        tradeTheirAccepted: false,
      };
    case "trade_update":
      return {
        ...state,
        tradeId: action.tradeId,
        tradeMyOffer: action.myOffer,
        tradeTheirOffer: action.theirOffer,
        tradeMyAccepted: action.myAccepted,
        tradeTheirAccepted: action.theirAccepted,
      };
    case "trade_close":
      return {
        ...state,
        tradeOpen: false,
        tradeId: null,
        tradeOtherPlayer: null,
        tradeMyOffer: {},
        tradeTheirOffer: {},
        tradeMyAccepted: false,
        tradeTheirAccepted: false,
      };
    case "combat_target_update": {
      const target = action.data as UiState["combatTarget"];
      return {
        ...state,
        combatTarget: target,
        healthBarVisible: target?.targetId ? true : state.healthBarVisible,
      };
    }
    case "player_status_update": {
      const next = action.data as UiState["playerStatus"];
      const damaged = state.playerStatus != null && next != null && next.currentHp < state.playerStatus.currentHp;
      return {
        ...state,
        playerStatus: next,
        playerDowned: false,
        healthBarVisible: state.healthBarVisible || damaged,
      };
    }
    case "player_downed":
      return { ...state, playerDowned: true };
    case "player_respawned": {
      const { currentHp, currentMana } = action.data as { currentHp: number; currentMana: number };
      return {
        ...state,
        playerDowned: false,
        playerStatus: state.playerStatus ? { ...state.playerStatus, currentHp, currentMana } : state.playerStatus,
      };
    }
    case "xp_gained":
      return { ...state, xpState: action.data as UiState["xpState"] };
    case "health_update":
    case "status_effect_update":
      return state;
  }
}
