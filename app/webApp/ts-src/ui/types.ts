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

export interface PreferencesData {
  subscribedChannels: string[];
  knownChannels: string[];
  disabledCommands: string[];
  shadersEnabled: boolean;
  commands: CommandInfo[];
  keybindings: Record<string, string[]>;
}
