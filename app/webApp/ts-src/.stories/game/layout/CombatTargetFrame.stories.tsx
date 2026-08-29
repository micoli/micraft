import type { Meta, StoryObj } from "@storybook/react";
import { CombatTargetFrame } from "../../../game/components/target/CombatTargetFrame";

const meta: Meta<typeof CombatTargetFrame> = {
  title: "Game/Layout/CombatTargetFrame",
  component: CombatTargetFrame,
  parameters: { layout: "centered" },
};
export default meta;

type Story = StoryObj<typeof CombatTargetFrame>;

export const Basic: Story = {
  args: {
    target: {
      targetId: "npc-42",
      displayName: "Gobelin",
      currentHp: 34,
      maxHp: 50,
      level: 7,
      distance: 4.2,
      targetOfTarget: null,
    },
  },
};

export const WithTargetOfTarget: Story = {
  args: {
    target: {
      targetId: "npc-42",
      displayName: "Chef Orc",
      currentHp: 180,
      maxHp: 200,
      level: 15,
      distance: 9.6,
      targetOfTarget: { id: "player-1", name: "alice", currentHp: 40, maxHp: 100 },
    },
  },
};

export const NoTarget: Story = {
  args: {
    target: {
      targetId: null,
      displayName: null,
      currentHp: 0,
      maxHp: 0,
      distance: null,
      targetOfTarget: null,
    },
  },
};
