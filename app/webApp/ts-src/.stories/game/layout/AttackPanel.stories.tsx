import type { Meta, StoryObj } from "@storybook/react-vite";
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
    rank: 1,
  },
  fireball: {
    damageType: "FIRE",
    manaCost: 12,
    rageCost: 0,
    cooldownMs: 3000,
    power: 14,
    weaponDice: "2d6",
    attackId: "fireball",
    rank: 3,
  },
  venom: {
    damageType: "POISON",
    manaCost: 6,
    rageCost: 0,
    cooldownMs: 1500,
    power: 8,
    weaponDice: "1d8",
    attackId: "venom",
    rank: 2,
  },
};

const spellTreeMeta: Record<string, SpellMeta> = {
  spark: {
    type: "DAMAGE",
    rageGain: 0,
    tokenCost: 0,
    manaCost: 6,
    rageCost: 0,
    cooldownMs: 800,
    aoeRadius: 0,
    maxRange: 15,
    power: 6,
    spellId: "spark",
    rank: 1,
  },
  bloodrite: {
    type: "BUFF",
    rageGain: 0,
    tokenCost: 2,
    manaCost: 0,
    rageCost: 0,
    cooldownMs: 8000,
    aoeRadius: 0,
    maxRange: 0,
    power: 0,
    spellId: "bloodrite",
    rank: 1,
  },
  frostnova: {
    type: "DAMAGE",
    rageGain: 0,
    tokenCost: 0,
    manaCost: 18,
    rageCost: 0,
    cooldownMs: 6000,
    aoeRadius: 4,
    maxRange: 0,
    power: 10,
    spellId: "frostnova",
    rank: 2,
  },
  mendflesh: {
    type: "HEAL",
    rageGain: 0,
    tokenCost: 1,
    manaCost: 14,
    rageCost: 0,
    cooldownMs: 4000,
    aoeRadius: 0,
    maxRange: 15,
    power: 20,
    spellId: "mendflesh",
    rank: 2,
  },
  arcanebolt: {
    type: "DAMAGE",
    rageGain: 0,
    tokenCost: 0,
    manaCost: 9,
    rageCost: 0,
    cooldownMs: 1200,
    aoeRadius: 0,
    maxRange: 20,
    power: 12,
    spellId: "arcanebolt",
    rank: 3,
  },
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
  args: { attackMeta, spellMeta: spellTreeMeta, pinnedMacros: ["heal-rotation", "aoe-pull"] },
};

export const OnCooldownAndOutOfResources: Story = {
  args: {
    attackMeta,
    spellMeta: spellTreeMeta,
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

export const SpellTree: Story = {
  args: { attackMeta: {}, spellMeta: spellTreeMeta },
};

export const SpellTreeWithLockedRanks: Story = {
  args: {
    attackMeta: {},
    spellMeta: spellTreeMeta,
    unlockedSpellIds: new Set(["spark", "bloodrite"]),
  },
};
