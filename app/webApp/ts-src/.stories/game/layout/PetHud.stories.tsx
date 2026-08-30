import type { Meta, StoryObj } from "@storybook/react";
import { PetHud } from "../../../game/components/pet/PetHud";
import type { PetInfo } from "../../../game/types";

const meta: Meta<typeof PetHud> = {
  title: "Game/Layout/PetHud",
  component: PetHud,
  parameters: { layout: "centered" },
  args: { layoutStyle: {}, onCommand: () => {} },
};
export default meta;

type Story = StoryObj<typeof PetHud>;

const pet = (over: Partial<PetInfo>): PetInfo => ({
  id: "p1",
  name: "Fang",
  npcType: "wolf",
  level: 4,
  xp: 120,
  currentHp: 26,
  maxHp: 40,
  spawned: true,
  dead: false,
  resurrectReadyAtMs: 0,
  ...over,
});

export const ActivePet: Story = {
  args: { roster: { pets: [pet({})], activePetId: "p1" } },
};

export const ActivePetLowHp: Story = {
  args: { roster: { pets: [pet({ currentHp: 3 })], activePetId: "p1" } },
};

export const DeadOnCooldown: Story = {
  args: {
    roster: {
      pets: [pet({ dead: true, spawned: false, currentHp: 0, resurrectReadyAtMs: Date.now() + 42_000 })],
      activePetId: null,
    },
  },
};

export const DeadReadyToRevive: Story = {
  args: {
    roster: {
      pets: [pet({ dead: true, spawned: false, currentHp: 0, resurrectReadyAtMs: Date.now() - 1000 })],
      activePetId: null,
    },
  },
};

export const RosterActiveAndDead: Story = {
  args: {
    roster: {
      pets: [
        pet({ id: "p1", name: "Fang" }),
        pet({
          id: "p2",
          name: "Whiskers",
          npcType: "cat",
          level: 2,
          dead: true,
          spawned: false,
          currentHp: 0,
          resurrectReadyAtMs: Date.now() + 15_000,
        }),
      ],
      activePetId: "p1",
    },
  },
};

export const NoPets: Story = {
  args: { roster: { pets: [], activePetId: null } },
};
