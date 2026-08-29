import type { Meta, StoryObj } from "@storybook/react";
import { PlayerStatusBar } from "../../../game/components/playerStatus/PlayerStatusBar";
import type { PlayerStatusData } from "../../../game/types";
import { stubMcState } from "../../_support/mcState";

const base: PlayerStatusData = {
  currentHp: 72,
  maxHp: 100,
  currentMana: 40,
  maxMana: 60,
  currentRage: 0,
  maxRage: 0,
  currentTokens: 2,
  maxTokens: 5,
  stance: "standing",
  globalCooldownRemainingMs: 0,
  attackCooldownsRemainingMs: {},
  godMode: false,
};

const meta: Meta<typeof PlayerStatusBar> = {
  title: "Game/Layout/PlayerStatusBar",
  component: PlayerStatusBar,
  parameters: { layout: "centered" },
  decorators: [stubMcState()],
};
export default meta;

type Story = StoryObj<typeof PlayerStatusBar>;

export const Caster: Story = { args: { status: base } };

export const Warrior: Story = {
  args: { status: { ...base, maxMana: 0, currentRage: 45, maxRage: 100 } },
};

export const OnGlobalCooldown: Story = {
  args: { status: { ...base, globalCooldownRemainingMs: 900 } },
};

export const GodMode: Story = {
  args: { status: { ...base, godMode: true }, godMode: true },
};

export const LowHp: Story = {
  args: { status: { ...base, currentHp: 8 } },
};
