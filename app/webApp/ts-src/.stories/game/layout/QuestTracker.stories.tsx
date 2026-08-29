import type { Meta, StoryObj } from "@storybook/react";
import { QuestTracker } from "../../../game/components/quest/QuestTracker";
import type { QuestProgress } from "../../../game/types";
import { mockApi } from "../../_support/mockApi";

const definitions = [
  {
    id: "q_kill_goblins",
    title: "Menace gobeline",
    type: "KILL",
    objectives: [{ npcType: "GOBLIN", requiredCount: 10 }],
    itemType: null,
    requiredCount: 0,
  },
  {
    id: "q_boss_orc",
    title: "Le Chef Orc",
    type: "BOSS",
    objectives: [
      { npcType: "ORC_GRUNT", requiredCount: 5 },
      { npcType: "ORC_CHIEF", requiredCount: 1 },
    ],
    itemType: null,
    requiredCount: 0,
  },
  {
    id: "q_fetch_flint",
    title: "Collecte de silex",
    type: "FETCH",
    objectives: [],
    itemType: "FLINT",
    requiredCount: 8,
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
  q_boss_orc: {
    status: "IN_PROGRESS",
    progress: { ORC_GRUNT: 5, ORC_CHIEF: 0 },
    acceptedAt: 1,
    completedAt: null,
    lastCompletedAt: null,
  },
  q_fetch_flint: {
    status: "IN_PROGRESS",
    progress: { FLINT: 3 },
    acceptedAt: 1,
    completedAt: null,
    lastCompletedAt: null,
  },
  q_done: { status: "COMPLETED", progress: {}, acceptedAt: 1, completedAt: 2, lastCompletedAt: 2 },
};

const meta: Meta<typeof QuestTracker> = {
  title: "Game/Layout/QuestTracker",
  component: QuestTracker,
  parameters: { layout: "centered" },
  decorators: [mockApi({ "/api/quests": definitions })],
};
export default meta;

type Story = StoryObj<typeof QuestTracker>;

export const Tracking: Story = {
  args: { visible: true, quests, layoutStyle: { position: "relative", width: 260 } },
};

export const Hidden: Story = {
  args: { visible: false, quests, layoutStyle: {} },
};

export const NoActiveQuests: Story = {
  args: {
    visible: true,
    quests: { q_done: quests.q_done },
    layoutStyle: { position: "relative", width: 260 },
  },
};
