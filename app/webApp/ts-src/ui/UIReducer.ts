import { GameLayout, HudData, HudMode, LogEntry, NpcDialogData, PreferencesData } from "./types";

export interface UiState {
  hud: HudData | null;
  hudMode: HudMode;
  notif: { msg: string; key: number } | null;
  logs: LogEntry[];
  logVisible: boolean;
  logKey: number;
  subscribedChannels: string[];
  knownChannels: string[];
  activeChannel: string;
  unreadChannels: string[];
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  hotbarVisible: boolean;
  shortcutBar: (string | null)[];
  selectedSlot: number;
  consoleOpen: boolean;
  loginVisible: boolean;
  disconnectMsg: string | null;
  layouts: GameLayout[];
  activeLayout: string;
  layoutEditorOpen: boolean;
  npcDialog: NpcDialogData | null;
  codexOpen: boolean;
  preferencesOpen: boolean;
  preferences: PreferencesData | null;
  pauseMenuOpen: boolean;
}

export type UiAction =
  | { type: "hud"; data: HudData }
  | { type: "hud_mode_cycle" }
  | { type: "notification"; msg: string }
  | { type: "log"; msg: string; channel: string }
  | { type: "chat_message"; channel: string; sender: string; msg: string }
  | { type: "channels_sync"; subscribed: string[]; known: string[] }
  | { type: "active_channel_select"; channel: string }
  | { type: "inventory"; data: Record<string, number> }
  | { type: "item_meta_loaded"; data: Record<string, { label: string; bg: string }> }
  | { type: "hotbar_toggle" }
  | { type: "shortcut_bar_update"; data: { slots: (string | null)[]; selected: number } }
  | { type: "slot_select"; slot: number }
  | { type: "console_show" }
  | { type: "console_hide" }
  | { type: "login_show" }
  | { type: "login_hide" }
  | { type: "disconnect_show"; message: string }
  | { type: "disconnect_hide" }
  | { type: "log_hide" }
  | { type: "layouts_sync"; layouts: GameLayout[]; activeLayout: string }
  | { type: "layout_editor_show" }
  | { type: "layout_editor_hide" }
  | { type: "layout_editor_save"; layouts: GameLayout[]; activeLayout: string }
  | { type: "npc_dialog_open"; payload: NpcDialogData }
  | { type: "npc_dialog_close" }
  | { type: "codex_open" }
  | { type: "codex_close" }
  | { type: "preferences_sync"; data: PreferencesData }
  | { type: "preferences_show" }
  | { type: "preferences_hide" }
  | {
      type: "preferences_save";
      subscribedChannels: string[];
      disabledCommands: string[];
      shadersEnabled: boolean;
      animatedFavicon: boolean;
      chunkDebugVisible: boolean;
      keybindings: Record<string, string[]>;
      customCommands: Record<string, string[]>;
    }
  | { type: "pause_menu_show" }
  | { type: "pause_menu_hide" };

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
      const unreadChannels =
        action.channel !== state.activeChannel && !state.unreadChannels.includes(action.channel)
          ? [...state.unreadChannels, action.channel]
          : state.unreadChannels;
      return { ...state, logs, logVisible: true, logKey: state.logKey + 1, unreadChannels };
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
    case "hotbar_toggle":
      return { ...state, hotbarVisible: !state.hotbarVisible };
    case "shortcut_bar_update":
      return { ...state, shortcutBar: action.data.slots, selectedSlot: action.data.selected };
    case "slot_select":
      return { ...state, selectedSlot: action.slot };
    case "console_show":
      return { ...state, consoleOpen: true };
    case "console_hide":
      return { ...state, consoleOpen: false };
    case "login_show":
      return { ...state, loginVisible: true };
    case "login_hide":
      return { ...state, loginVisible: false };
    case "disconnect_show":
      return { ...state, disconnectMsg: action.message };
    case "disconnect_hide":
      return { ...state, disconnectMsg: null };
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
          }
        : state.preferences;
      return { ...state, preferences: prefs, preferencesOpen: false };
    }
    case "pause_menu_show":
      return { ...state, pauseMenuOpen: true };
    case "pause_menu_hide":
      return { ...state, pauseMenuOpen: false };
  }
}
