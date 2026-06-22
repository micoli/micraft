export interface HudData {
  x: number; y: number; z: number;
  yaw: number; pitch: number;
  stance: string; speed: number;
  fps: number; kbIn: number; kbOut: number;
  biome: string; targetBlock: string;
}

export interface LogEntry {
  time: string;
  msg: string;
}

export interface UiState {
  hud: HudData | null;
  notif: { msg: string; key: number } | null;
  logs: LogEntry[];
  inventory: Record<string, number>;
  hotbarVisible: boolean;
  consoleOpen: boolean;
  loginVisible: boolean;
  disconnectMsg: string | null;
}

export type UiAction =
  | { type: 'hud'; data: HudData }
  | { type: 'notification'; msg: string }
  | { type: 'log'; msg: string }
  | { type: 'inventory'; data: Record<string, number> }
  | { type: 'hotbar_toggle' }
  | { type: 'console_show' }
  | { type: 'console_hide' }
  | { type: 'login_show' }
  | { type: 'login_hide' }
  | { type: 'disconnect_show'; message: string }
  | { type: 'disconnect_hide' };
