import type { Meta, StoryObj } from "@storybook/react";
import { FactionPanel } from "../../../../game/components/social/FactionPanel";
import type { FactionSyncData } from "../../../../game/types";
import { stubMcState } from "../../../_support/mcState";

const definitions = [
  { id: "red", name: "Ordre Écarlate", color: "#c0392b", description: "" },
  { id: "blue", name: "Pacte Azur", color: "#2980b9", description: "" },
  { id: "green", name: "Cercle Sylvestre", color: "#27ae60", description: "" },
];

const base: FactionSyncData = {
  enabled: true,
  definitions,
  states: [
    { id: "red", memberCount: 12 },
    { id: "blue", memberCount: 8 },
    { id: "green", memberCount: 5 },
  ],
  myFactionId: "blue",
  changeCooldownRemainingMs: 0,
};

const meta: Meta<typeof FactionPanel> = {
  title: "Game/Windows/Social/FactionPanel",
  component: FactionPanel,
  parameters: { layout: "fullscreen" },
  decorators: [stubMcState()],
  args: { open: true, onClose: () => {} },
};
export default meta;

type Story = StoryObj<typeof FactionPanel>;

export const Member: Story = { args: { faction: base } };

export const NoFactionYet: Story = { args: { faction: { ...base, myFactionId: null } } };

export const OnCooldown: Story = {
  args: { faction: { ...base, changeCooldownRemainingMs: 45000 } },
};

export const Disabled: Story = { args: { faction: { ...base, enabled: false } } };
