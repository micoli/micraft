import type { Meta, StoryObj } from "@storybook/react";
import { BuffBar } from "../../../game/components/buffs/BuffBar";

const meta: Meta<typeof BuffBar> = {
  title: "Game/Layout/BuffBar",
  component: BuffBar,
  parameters: { layout: "centered" },
};
export default meta;

type Story = StoryObj<typeof BuffBar>;

const inMs = (s: number) => Date.now() + s * 1000;

export const Single: Story = {
  args: { effects: [{ name: "Regeneration", expiresAtMs: inMs(30) }] },
};

export const Many: Story = {
  args: {
    effects: [
      { name: "Regeneration", expiresAtMs: inMs(30) },
      { name: "Strength", expiresAtMs: inMs(120) },
      { name: "Haste", expiresAtMs: inMs(8) },
      { name: "Shield", expiresAtMs: inMs(300) },
    ],
  },
};

export const AllExpired: Story = {
  args: { effects: [{ name: "Regeneration", expiresAtMs: inMs(-5) }] },
};

export const Empty: Story = {
  args: { effects: [] },
};
