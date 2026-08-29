import type { Meta, StoryObj } from "@storybook/react";
import { AggroIndicators } from "../../../game/components/character/AggroIndicators";
import type { NpcProximityEntry } from "../../../game/types";

const npc = (o: Partial<NpcProximityEntry>): NpcProximityEntry => ({
  id: "npc-1",
  name: "Gobelin",
  relAngle: 0,
  dist: 8,
  aggro: false,
  ...o,
});

const meta: Meta<typeof AggroIndicators> = {
  title: "Game/Layout/AggroIndicators",
  component: AggroIndicators,
  parameters: { layout: "centered" },
};
export default meta;

type Story = StoryObj<typeof AggroIndicators>;

export const SingleCalm: Story = {
  args: { npcProximity: [npc({ dist: 12, relAngle: Math.PI / 4 })] },
};

export const SingleAggro: Story = {
  args: { npcProximity: [npc({ name: "Loup enragé", dist: 4, relAngle: -Math.PI / 2, aggro: true })] },
};

export const Crowd: Story = {
  args: {
    npcProximity: [
      npc({ id: "n1", name: "Gobelin", dist: 6, relAngle: 0, aggro: true }),
      npc({ id: "n2", name: "Rat géant des cavernes", dist: 11, relAngle: Math.PI / 3 }),
      npc({ id: "n3", name: "Squelette", dist: 18, relAngle: Math.PI, aggro: true }),
      npc({ id: "n4", name: "Chauve-souris", dist: 3, relAngle: -Math.PI / 6 }),
    ],
  },
};

export const Empty: Story = {
  args: { npcProximity: [] },
};
