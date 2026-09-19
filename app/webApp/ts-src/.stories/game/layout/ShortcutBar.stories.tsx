import type { Meta, StoryObj } from "@storybook/react-vite";
import { ShortcutBar } from "../../../game/components/shortcutBar/ShortcutBar";
import type { AttackMeta, ItemMetaEntry, ShortcutSlot, SpellMeta } from "../../../game/types";
import { stubMcState } from "../../_support/mcState";

const itemMeta: Record<string, ItemMetaEntry> = {
  COBBLESTONE: { label: "Cobblestone", bg: "#888888" },
  DIRT: { label: "Dirt", bg: "#7a5230" },
  SAND: { label: "Sand", bg: "#e0d090" },
};

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
};

const spellMeta: Record<string, SpellMeta> = {
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
    rank: 1,
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
    rank: 3,
  },
};

const emptySlots: (ShortcutSlot | null)[] = Array(10).fill(null);

const mixedSlots: (ShortcutSlot | null)[] = [
  { kind: "item", id: "COBBLESTONE" },
  { kind: "attack", id: "slash" },
  { kind: "attack", id: "fireball" },
  { kind: "spell", id: "bloodrite" },
  { kind: "macro", id: "heal-rotation" },
  { kind: "item", id: "DIRT" },
  null,
  { kind: "item", id: "SAND" },
  null,
  null,
];

const spellSlots: (ShortcutSlot | null)[] = [
  { kind: "spell", id: "bloodrite" },
  { kind: "spell", id: "frostnova" },
  { kind: "spell", id: "arcanebolt" },
  { kind: "spell", id: "mendflesh" },
  null,
  null,
  null,
  null,
  null,
  null,
];

const meta: Meta<typeof ShortcutBar> = {
  title: "Game/Layout/ShortcutBar",
  component: ShortcutBar,
  parameters: { layout: "fullscreen" },
  decorators: [stubMcState({ bindings: {} })],
};
export default meta;

type Story = StoryObj<typeof ShortcutBar>;

export const Empty: Story = {
  args: {
    inventory: {},
    itemMeta: {},
    attackMeta: {},
    slots: emptySlots,
    selectedSlot: 0,
    onSlotDrop: () => {},
  },
};

export const MixedContent: Story = {
  args: {
    inventory: { COBBLESTONE: 64, DIRT: 12, SAND: 0 },
    itemMeta,
    attackMeta,
    spellMeta,
    slots: mixedSlots,
    selectedSlot: 2,
    macros: { "heal-rotation": "/cast heal\n/cast regen" },
    macroIcons: { "heal-rotation": "✚" },
    onSlotDrop: () => {},
  },
};

export const MultiplePages: Story = {
  args: {
    inventory: { COBBLESTONE: 64, DIRT: 12, SAND: 8 },
    itemMeta,
    attackMeta,
    spellMeta,
    slots: mixedSlots,
    selectedSlot: 4,
    currentPage: 1,
    nonEmptyPages: [0, 1, 2],
    macros: { "heal-rotation": "/cast heal" },
    macroIcons: { "heal-rotation": "✚" },
    onSlotDrop: () => {},
  },
};

export const SpellsShowcase: Story = {
  args: {
    inventory: {},
    itemMeta: {},
    attackMeta: {},
    spellMeta,
    slots: spellSlots,
    selectedSlot: 1,
    onSlotDrop: () => {},
  },
};

export const OnCooldown: Story = {
  args: {
    inventory: {},
    itemMeta,
    attackMeta,
    slots: mixedSlots,
    selectedSlot: 1,
    onSlotDrop: () => {},
    playerStatus: {
      currentHp: 80,
      maxHp: 100,
      currentMana: 40,
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
