import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Character } from "../../../game/components/character/Character";
import type { ArmorSlots } from "../../../game/components/character/Character";
import type { AttackMeta, CharacterSyncData } from "../../../game/types";
import { stubMcState } from "../../_support/mcState";
import { mockApi } from "../../_support/mockApi";

const NO_SLOTS: ArmorSlots = {
  head: false,
  body: false,
  cape: false,
  rightBiceps: false,
  rightForearm: false,
  rightHand: false,
  leftBiceps: false,
  leftForearm: false,
  leftHand: false,
  rightThigh: false,
  rightCalf: false,
  rightFoot: false,
  leftThigh: false,
  leftCalf: false,
  leftFoot: false,
};

const armors = {
  iron_helm: { wearable: { ...NO_SLOTS, head: true }, statBonus: { con: 2 } },
  iron_plate: { wearable: { ...NO_SLOTS, body: true }, statBonus: { con: 4, str: 1 } },
  leather_boots: { wearable: { ...NO_SLOTS, leftFoot: true, rightFoot: true }, statBonus: { dex: 1 } },
};

const weapons = { longsword: { category: "SWORD" }, oak_bow: { category: "BOW" } };
const tools = { iron_pickaxe: { category: "PICKAXE" } };

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
};

const characterSyncData: CharacterSyncData = {
  character: {
    id: "player-1",
    name: "alice",
    characterClass: "WARRIOR",
    level: 6,
    xp: 320,
    baseStats: { str: 14, dex: 10, intel: 8, wis: 9, con: 13, cha: 10 },
    currentHp: 88,
    currentMana: 20,
  },
  derived: {
    maxHp: 120,
    maxMana: 30,
    meleeDmg: 18,
    rangedDmg: 9,
    spellDmg: 4,
    critChancePct: 8,
    critDmgMult: 1.5,
    dodgePct: 6,
    magicResistPct: 4,
    initiative: 11,
    hpRegenPerSec: 1.2,
    manaRegenPerSec: 0.4,
  },
  effectiveBaseStats: { str: 16, dex: 10, intel: 8, wis: 9, con: 15, cha: 10 },
};

const api = mockApi({
  "/api/armors": armors,
  "/api/weapons": weapons,
  "/api/tools": tools,
  "/api/player/player-1/armors": ["iron_helm"],
  "/api/player/player-1/skin": { skin: "articulated" },
  "/api/player/player-1/hands": { dominantHand: "RIGHT", rightHandItem: "longsword", leftHandItem: null },
  "/api/player/player-1/owned": {
    armors: ["iron_helm", "iron_plate", "leather_boots"],
    weapons: ["longsword", "oak_bow"],
    tools: ["iron_pickaxe"],
  },
});

const meta: Meta<typeof Character> = {
  title: "Game/Windows/Character",
  component: Character,
  parameters: { layout: "fullscreen" },
  decorators: [api, stubMcState()],
  args: { open: true, onClose: fn(), onCommand: fn(), attackMeta },
};
export default meta;

type Story = StoryObj<typeof Character>;

export const Equipment: Story = {};

export const WithStats: Story = { args: { characterSyncData } };

export const Closed: Story = { args: { open: false } };
