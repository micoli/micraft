import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import { QuestJournal } from "../../../game/components/quest/QuestJournal";
import type { QuestProgress } from "../../../game/types";
import { mockApi } from "../../_support/mockApi";

const definitions = [
  {
    id: "q_kill_goblins",
    title: "Menace gobeline",
    description: "Des gobelins ont été aperçus près du village. Réduisez leur nombre.",
    type: "KILL",
    level: 1,
    objectives: [{ npcType: "GOBLIN", requiredCount: 10 }],
    itemType: null,
    requiredCount: 0,
    rewards: { xp: 50, items: [{ type: "COBBLESTONE", count: 5 }] },
    dependsOn: [],
    repeatable: false,
    cooldownSeconds: 0,
  },
  {
    id: "q_boss_orc",
    title: "Le Chef Orc",
    description: "Un chef orc terrorise la région. Éliminez-le avec sa garde rapprochée.",
    type: "BOSS",
    level: 4,
    objectives: [
      { npcType: "ORC_GRUNT", requiredCount: 5 },
      { npcType: "ORC_CHIEF", requiredCount: 1 },
    ],
    itemType: null,
    requiredCount: 0,
    rewards: { xp: 300, items: [{ type: "FLINT", count: 2 }] },
    dependsOn: ["q_kill_goblins"],
    repeatable: false,
    cooldownSeconds: 0,
  },
  {
    id: "q_fetch_flint",
    title: "Collecte de silex",
    description: "Un artisan local a besoin de silex pour ses outils.",
    type: "FETCH",
    level: 1,
    objectives: [],
    itemType: "FLINT",
    requiredCount: 8,
    rewards: { xp: 20, items: [] },
    dependsOn: [],
    repeatable: true,
    cooldownSeconds: 3600,
  },
];

const quests: Record<string, QuestProgress> = {
  q_kill_goblins: {
    status: "IN_PROGRESS",
    progress: { GOBLIN: 4 },
    acceptedAt: 1,
    completedAt: null,
    lastCompletedAt: null,
  },
  q_boss_orc: { status: "TODO", progress: {}, acceptedAt: null, completedAt: null, lastCompletedAt: null },
  q_fetch_flint: {
    status: "COMPLETED",
    progress: { FLINT: 8 },
    acceptedAt: 1,
    completedAt: 2,
    lastCompletedAt: Date.now(),
  },
};

const meta: Meta<typeof QuestJournal> = {
  title: "Game/Windows/QuestJournal",
  component: QuestJournal,
  parameters: { layout: "fullscreen" },
  decorators: [mockApi({ "/api/quests": definitions })],
  args: { onClose: fn(), onCommand: fn() },
};
export default meta;

type Story = StoryObj<typeof QuestJournal>;

export const Default: Story = {
  args: { open: true, quests, playerLevel: 5 },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("Quest Journal")).toBeVisible();
    // Selected quest's title is shown both in the sidebar list and the detail heading (scope to
    // the heading to avoid an ambiguous match) — findBy* waits for the async /api/quests fetch
    // and selection to land before querying, unlike getBy* which throws immediately.
    await expect(await body.findByRole("heading", { name: "Menace gobeline" })).toBeVisible();
  },
};

export const DependencyLocked: Story = {
  name: "Dépendance non remplie (Accept désactivé)",
  args: {
    open: true,
    quests: {
      q_boss_orc: { status: "TODO", progress: {}, acceptedAt: null, completedAt: null, lastCompletedAt: null },
    },
    playerLevel: 5,
  },
  play: async () => {
    const body = within(document.body);
    await body.findByRole("heading", { name: "Menace gobeline" });
    await (await body.findByText("Le Chef Orc")).click();
    await expect(body.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
  },
};

export const OnCooldown: Story = {
  name: "Répétable en cooldown",
  args: { open: true, quests, playerLevel: 5 },
  play: async () => {
    const body = within(document.body);
    await (await body.findByText("Collecte de silex")).click();
    await expect(body.getByText(/Cooldown:/)).toBeVisible();
  },
};

export const NoQuests: Story = {
  name: "Aucune quête disponible",
  decorators: [mockApi({ "/api/quests": [] })],
  args: { open: true, quests: {}, playerLevel: 5 },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("No quests")).toBeVisible();
  },
};

export const Closed: Story = {
  args: { open: false, quests, playerLevel: 5 },
};
