export interface HudData {
  x: number; y: number; z: number;
  yaw: number; pitch: number;
  stance: string; speed: number;
  fps: number; kbIn: number; kbOut: number;
  biome: string; targetBlock: string;
  gameTime: string;
}

export interface LogEntry {
  time: string;
  msg: string;
}

export type HudMode = 'simple' | 'medium' | 'complete';

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

export interface UiState {
  hud: HudData | null;
  hudMode: HudMode;
  notif: { msg: string; key: number } | null;
  logs: LogEntry[];
  logVisible: boolean;
  logKey: number;
  inventory: Record<string, number>;
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
}

export type UiAction =
  | { type: 'hud'; data: HudData }
  | { type: 'hud_mode_cycle' }
  | { type: 'notification'; msg: string }
  | { type: 'log'; msg: string }
  | { type: 'inventory'; data: Record<string, number> }
  | { type: 'hotbar_toggle' }
  | { type: 'shortcut_bar_update'; data: { slots: (string | null)[]; selected: number } }
  | { type: 'slot_select'; slot: number }
  | { type: 'console_show' }
  | { type: 'console_hide' }
  | { type: 'login_show' }
  | { type: 'login_hide' }
  | { type: 'disconnect_show'; message: string }
  | { type: 'disconnect_hide' }
  | { type: 'log_hide' }
  | { type: 'layouts_sync'; layouts: GameLayout[]; activeLayout: string }
  | { type: 'layout_editor_show' }
  | { type: 'layout_editor_hide' }
  | { type: 'layout_editor_save'; layouts: GameLayout[]; activeLayout: string }
  | { type: 'npc_dialog_open'; payload: NpcDialogData }
  | { type: 'npc_dialog_close' };
