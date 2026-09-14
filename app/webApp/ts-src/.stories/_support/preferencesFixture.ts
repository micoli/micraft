import type { PreferencesData } from "../../game/types";

export const keybindings: Record<string, string[]> = {
  move_forward: ["KeyW"],
  move_back: ["KeyS"],
  jump: ["Space"],
  inventory: ["KeyE"],
  open_chat: ["Enter"],
};

export const keybindingGroups: Record<string, string[]> = {
  movement: ["move_forward", "move_back", "jump"],
  game: ["inventory", "open_chat"],
};

export const preferences: PreferencesData = {
  subscribedChannels: [],
  knownChannels: ["global", "local", "trade", "system"],
  disabledCommands: [],
  shadersEnabled: true,
  dynamicFogEnabled: true,
  animatedFavicon: false,
  chunkDebugVisible: false,
  statisticsVisible: true,
  attackPanelVisible: true,
  commands: [
    { id: "c1", command: "/help", description: "List commands" },
    { id: "c2", command: "/equip", description: "Equip armor" },
    { id: "c3", command: "/lang", description: "Change language" },
  ],
  keybindings,
  defaultKeybindings: keybindings,
  keybindingGroups,
  customCommands: {},
  macros: {},
  fieldOfView: 90,
  autoTargetEnabled: true,
  continuousBreak: false,
  dominantHand: "RIGHT",
  disabledViewModes: [],
  turnSpeedHorizontal: 1.5,
  turnSpeedVertical: 1.0,
};
