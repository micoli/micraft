import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Preferences } from "../../../game/components/preferences/Preferences";
import type { PreferencesData } from "../../../game/types";
import { stubMcState } from "../../_support/mcState";

const keybindings: Record<string, string[]> = {
  move_forward: ["KeyW"],
  move_back: ["KeyS"],
  jump: ["Space"],
  inventory: ["KeyE"],
  open_chat: ["Enter"],
};

const preferences: PreferencesData = {
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

const meta: Meta<typeof Preferences> = {
  title: "Game/Windows/Preferences",
  component: Preferences,
  parameters: { layout: "fullscreen" },
  decorators: [stubMcState()],
  args: {
    open: true,
    preferences,
    fullMeshedChunks: 128,
    impostorMeshedChunks: 12,
    onSave: fn(),
    onClose: fn(),
    onLiveOverride: fn(),
  },
};
export default meta;

type Story = StoryObj<typeof Preferences>;

export const ChatTab: Story = { args: { initialTab: "chat" } };
export const GraphicsTab: Story = { args: { initialTab: "graphics" } };
export const GameTab: Story = { args: { initialTab: "game" } };
export const KeybindingsTab: Story = { args: { initialTab: "keybindings" } };
export const Closed: Story = { args: { open: false } };
