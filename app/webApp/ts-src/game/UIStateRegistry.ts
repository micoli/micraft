import {
  AttackMeta,
  ChannelSubscription,
  CharacterSyncData,
  ClassDefinitions,
  CombatTargetData,
  GameLayout,
  HudData,
  LogEntry,
  NpcDialogData,
  NpcProximityEntry,
  PlayerStatusData,
  PreferencesData,
  PreferencesSaveData,
  QuestProgress,
  RecipeDefinition,
  ShortcutSlot,
  SpellMeta,
  TradeData,
} from "./types";

export interface UiState {
  hud: HudData | null;
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
  ingameMapVisible: boolean;
  combatTarget: CombatTargetData | null;
  playerStatus: PlayerStatusData | null;
  playerDowned: boolean;
  xpState: { xpGained: number; totalXp: number; level: number; leveledUp: boolean; nextLevelXp: number } | null;
  trade: TradeData | null;
  classDefinitions: ClassDefinitions | null;
  npcProximity: NpcProximityEntry[];
  quests: Record<string, QuestProgress>;
  questJournalOpen: boolean;
  questTrackerVisible: boolean;
}

const ingameMapRegistry = {
  ingame_map_toggle: (state: UiState) => ({ ...state, ingameMapVisible: !state.ingameMapVisible }),
  ingame_map_open: (state: UiState) => ({ ...state, ingameMapVisible: true }),
  ingame_map_close: (state: UiState) => ({ ...state, ingameMapVisible: false }),
};

const tradeRegistry = {
  trade_open: (state: UiState, payload: { data: TradeData }) => ({ ...state, trade: payload.data }),
  trade_update: (state: UiState, payload: { data: TradeData }) => ({ ...state, trade: payload.data }),
  trade_close: (state: UiState) => ({ ...state, trade: null }),
};

const questRegistry = {
  quest_sync: (state: UiState, payload: { quests: Record<string, QuestProgress> }) => ({
    ...state,
    quests: payload.quests,
  }),
  quest_update: (state: UiState, payload: { questId: string; progress: QuestProgress }) => ({
    ...state,
    quests: { ...state.quests, [payload.questId]: payload.progress },
  }),
  quest_journal_open: (state: UiState) => ({ ...state, questJournalOpen: true }),
  quest_journal_close: (state: UiState) => ({ ...state, questJournalOpen: false }),
  quest_tracker_toggle: (state: UiState) => ({ ...state, questTrackerVisible: !state.questTrackerVisible }),
};

const MC_LOG_MAX = 100;
const consoleRegistry = {
  console_show: (state: UiState) => ({ ...state, consoleOpen: true }),
  console_hide: (state: UiState) => ({ ...state, consoleOpen: false }),
  log: (state: UiState, payload: { msg: string; channel: string }) => {
    const now = new Date();
    const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
    const entry: LogEntry = { time, msg: payload.msg, channel: payload.channel };
    const logs = [...state.logs, entry].slice(-MC_LOG_MAX);
    const unreadChannels =
      payload.channel !== state.activeChannel && !state.unreadChannels.includes(payload.channel)
        ? [...state.unreadChannels, payload.channel]
        : state.unreadChannels;
    return { ...state, logs, logVisible: true, logKey: state.logKey + 1, unreadChannels };
  },
  chat_message: (state: UiState, payload: { msg: string; channel: string; sender: string }) => {
    const now = new Date();
    const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
    const entry: LogEntry = { time, msg: payload.msg, channel: payload.channel, sender: payload.sender };
    const logs = [...state.logs, entry].slice(-MC_LOG_MAX);
    const autoFocus = state.subscribedChannels.find((c) => c.name === payload.channel)?.autoFocus ?? false;
    const activeChannel = autoFocus ? payload.channel : state.activeChannel;
    const unreadChannels = autoFocus
      ? state.unreadChannels.filter((c) => c !== payload.channel)
      : payload.channel !== state.activeChannel && !state.unreadChannels.includes(payload.channel)
        ? [...state.unreadChannels, payload.channel]
        : state.unreadChannels;
    return { ...state, logs, logVisible: true, logKey: state.logKey + 1, unreadChannels, activeChannel };
  },
  channels_sync: (state: UiState, payload: { subscribed: ChannelSubscription[]; known: string[] }) => ({
    ...state,
    subscribedChannels: payload.subscribed,
    knownChannels: payload.known,
  }),
  active_channel_select: (state: UiState, payload: { channel: string }) => ({
    ...state,
    activeChannel: payload.channel,
    unreadChannels: state.unreadChannels.filter((c) => c !== payload.channel),
  }),
};

const layoutEditorRegistry = {
  layout_editor_show: (state: UiState) => ({ ...state, layoutEditorOpen: true }),
  layout_editor_hide: (state: UiState) => ({ ...state, layoutEditorOpen: false }),
  layout_editor_save: (state: UiState, payload: { layouts: GameLayout[]; activeLayout: string }) => ({
    ...state,
    layouts: payload.layouts,
    activeLayout: payload.activeLayout,
    layoutEditorOpen: false,
  }),
  layouts_sync: (state: UiState, payload: { layouts: GameLayout[]; activeLayout: string }) => ({
    ...state,
    layouts: payload.layouts,
    activeLayout: payload.activeLayout,
  }),
};

const preferencesRegistry = {
  preferences_sync: (state: UiState, payload: { data: PreferencesData }) => ({ ...state, preferences: payload.data }),
  preferences_show: (state: UiState) => ({ ...state, preferencesOpen: true }),
  preferences_hide: (state: UiState) => ({ ...state, preferencesOpen: false }),
  preferences_save: (state: UiState, payload: { data: PreferencesSaveData }) => ({
    ...state,
    preferences: state.preferences ? { ...state.preferences, ...payload.data } : state.preferences,
    preferencesOpen: false,
  }),
};

const loadingRegistry = {
  item_meta_loaded: (state: UiState, payload: { data: Record<string, { label: string; bg: string }> }) => ({
    ...state,
    itemMeta: payload.data,
  }),
  attack_meta_loaded: (state: UiState, payload: { data: Record<string, AttackMeta> }) => ({
    ...state,
    attackMeta: payload.data,
  }),
  spell_meta_loaded: (state: UiState, payload: { data: Record<string, SpellMeta> }) => ({
    ...state,
    spellMeta: payload.data,
  }),
  class_definitions_loaded: (state: UiState, payload: { data: ClassDefinitions }) => ({
    ...state,
    classDefinitions: payload.data,
  }),
  chunk_loading_update: (state: UiState, payload: { meshed: number; downloaded: number; total: number }) => ({
    ...state,
    chunkLoading: payload,
  }),
  chunk_loading_hide: (state: UiState) => ({ ...state, chunkLoading: null }),
};

const componentVisibilityRegistry = {
  disconnect_show: (state: UiState, payload: { message: string }) => ({ ...state, disconnectMsg: payload.message }),
  disconnect_hide: (state: UiState) => ({ ...state, disconnectMsg: null }),
  npc_dialog_open: (state: UiState, payload: { data: NpcDialogData }) => ({ ...state, npcDialog: payload.data }),
  npc_dialog_close: (state: UiState) => ({ ...state, npcDialog: null }),
  codex_open: (state: UiState) => ({ ...state, codexOpen: true }),
  codex_close: (state: UiState) => ({ ...state, codexOpen: false }),
  craft_open: (state: UiState) => ({ ...state, craftOpen: true }),
  craft_close: (state: UiState) => ({ ...state, craftOpen: false }),
  pause_menu_show: (state: UiState) => ({ ...state, pauseMenuOpen: true }),
  pause_menu_hide: (state: UiState) => ({ ...state, pauseMenuOpen: false }),
  macro_editor_open: (state: UiState) => ({ ...state, macroEditorOpen: true }),
  macro_editor_close: (state: UiState) => ({ ...state, macroEditorOpen: false }),
  character_open: (state: UiState) => ({ ...state, characterOpen: true }),
  character_close: (state: UiState) => ({ ...state, characterOpen: false }),
  hotbar_toggle: (state: UiState) => ({ ...state, hotbarVisible: !state.hotbarVisible }),
  healthbar_toggle: (state: UiState) => ({ ...state, healthBarVisible: !state.healthBarVisible }),
  statistics_toggle: (state: UiState) => ({
    ...state,
    preferences: state.preferences
      ? { ...state.preferences, statisticsVisible: !(state.preferences.statisticsVisible ?? false) }
      : state.preferences,
  }),
  log_hide: (state: UiState) => ({ ...state, logVisible: false }),
};

const gameRegistry = {
  combat_target_update: (state: UiState, payload: { data: CombatTargetData | null }) => {
    const target = payload.data;
    return {
      ...state,
      combatTarget: target,
      healthBarVisible: target?.targetId ? true : state.healthBarVisible,
    };
  },
  health_update: (state: UiState, _payload: { data: unknown }) => state,
  player_status_update: (state: UiState, payload: { data: PlayerStatusData | null }) => {
    const next = payload.data;
    const damaged = state.playerStatus != null && next != null && next.currentHp < state.playerStatus.currentHp;
    return {
      ...state,
      playerStatus: next,
      playerDowned: next != null ? next.currentHp <= 0 : state.playerDowned,
      healthBarVisible: state.healthBarVisible || damaged,
    };
  },
  npc_proximity_update: (state: UiState, payload: { data: NpcProximityEntry[] }) => ({
    ...state,
    npcProximity: payload.data,
  }),
  status_effect_update: (state: UiState, _payload: { data: unknown }) => state,
  player_downed: (state: UiState, _payload: { playerId: string }) => ({ ...state, playerDowned: true }),
  player_respawned: (state: UiState, payload: { data: { currentHp: number; currentMana: number } }) => {
    const { currentHp, currentMana } = payload.data ;
    return {
      ...state,
      playerDowned: false,
      playerStatus: state.playerStatus ? { ...state.playerStatus, currentHp, currentMana } : state.playerStatus,
    };
  },
  xp_gained: (state: UiState, payload: { data: unknown }) => ({
    ...state,
    xpState: payload.data as UiState["xpState"],
  }),
};

let notificationsKey = 0;
const globalRegistry = {
  hud: (state: UiState, payload: { data: HudData }) => ({ ...state, hud: payload.data }),
  notification: (state: UiState, payload: { msg: string }) => ({
    ...state,
    notif: { msg: payload.msg, key: ++notificationsKey },
  }),
  inventory: (state: UiState, payload: { data: Record<string, number> }) => ({ ...state, inventory: payload.data }),
  shortcut_bar_update: (state: UiState, payload: { data: { slots: (ShortcutSlot | null)[]; selected: number } }) => ({
    ...state,
    shortcutBar: payload.data.slots,
    selectedSlot: payload.data.selected,
  }),
  slot_select: (state: UiState, payload: { slot: number }) => ({ ...state, selectedSlot: payload.slot }),
  craft_sync: (state: UiState, payload: { recipes: Record<string, RecipeDefinition>; knownRecipes: string[] }) => ({
    ...state,
    craftRecipes: payload.recipes,
    craftKnownRecipes: payload.knownRecipes,
  }),
  character_sync: (state: UiState, payload: { data: CharacterSyncData }) => ({
    ...state,
    characterSyncData: payload.data,
  }),
};
export const actionRegistry = {
  ...componentVisibilityRegistry,
  ...consoleRegistry,
  ...gameRegistry,
  ...globalRegistry,
  ...ingameMapRegistry,
  ...layoutEditorRegistry,
  ...loadingRegistry,
  ...preferencesRegistry,
  ...questRegistry,
  ...tradeRegistry,
};
