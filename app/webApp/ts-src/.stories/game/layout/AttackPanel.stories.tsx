import type { Meta, StoryObj } from "@storybook/react";
import { AttackPanel } from "../../../game/components/AttackPanel";
import type { AttackMeta, SpellMeta } from "../../../game/types";
import { stubMcState } from "../../_support/mcState";

const attackMeta: Record<string, AttackMeta> = {
  slash: {
    damageType: "PHYSICAL",
    manaCost: 0,
    rageCost: 0,
    cooldownMs: 0,
    power: 5,
    weaponDice: "1d6",
    attackId: "slash",
    level: 1,
  },
  fireball: {
    damageType: "FIRE",
    manaCost: 12,
    rageCost: 0,
    cooldownMs: 3000,
    power: 14,
    weaponDice: "2d6",
    attackId: "fireball",
    level: 3,
  },
  venom: {
    damageType: "POISON",
    manaCost: 6,
    rageCost: 0,
    cooldownMs: 1500,
    power: 8,
    weaponDice: "1d8",
    attackId: "venom",
    level: 2,
  },
};

const spellMeta: Record<string, SpellMeta> = {
  bloodrite: { type: "BUFF", rageGain: 0, tokenCost: 2, manaCost: 0, rageCost: 0, cooldownMs: 8000, aoeRadius: 0 },
};

const meta: Meta<typeof AttackPanel> = {
  title: "Game/Layout/AttackPanel",
  component: AttackPanel,
  parameters: { layout: "centered" },
  decorators: [stubMcState()],
};
export default meta;

type Story = StoryObj<typeof AttackPanel>;

export const AttacksOnly: Story = {
  args: { attackMeta },
};

export const WithSpellsAndMacros: Story = {
  args: { attackMeta, spellMeta, pinnedMacros: ["heal-rotation", "aoe-pull"] },
};

export const OnCooldownAndOutOfResources: Story = {
  args: {
    attackMeta,
    spellMeta,
    playerStatus: {
      currentHp: 50,
      maxHp: 100,
      currentMana: 3,
      maxMana: 60,
      currentRage: 0,
      maxRage: 0,
      currentTokens: 0,
      maxTokens: 5,
      stance: "standing",
      globalCooldownRemainingMs: 0,
      attackCooldownsRemainingMs: { fireball: 2200 },
      godMode: false,
    },
  },
};

export const Empty: Story = {
  args: { attackMeta: {} },
};
