import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import { NpcQuestDialog } from "../../../game/components/npc/NpcQuestDialog";
import type { QuestGiverDialogData } from "../../../game/types";

const data: QuestGiverDialogData = {
  npcId: "npc-1",
  npcType: "VILLAGER",
  turnInable: ["q_kill_goblins"],
  offerable: [
    {
      id: "q_fetch_flint",
      title: "Collecte de silex",
      description: "Un artisan local a besoin de silex pour ses outils.",
      level: 1,
    },
    {
      id: "q_boss_orc",
      title: "Le Chef Orc",
      description: "Un chef orc terrorise la région. Éliminez-le avec sa garde rapprochée.",
      level: 4,
    },
  ],
};

const meta: Meta<typeof NpcQuestDialog> = {
  title: "Game/Windows/NpcQuestDialog",
  component: NpcQuestDialog,
  parameters: { layout: "centered" },
  args: { onClose: fn(), onAccept: fn() },
};
export default meta;

type Story = StoryObj<typeof NpcQuestDialog>;

export const Default: Story = {
  args: { data },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("VILLAGER")).toBeVisible();
    await expect(body.getByText("Le Chef Orc")).toBeVisible();
    await expect(body.getByText("q_kill_goblins")).toBeVisible();
  },
};

export const NoTurnIns: Story = {
  name: "Sans quête à rendre",
  args: { data: { ...data, turnInable: [] } },
};

export const NoOfferable: Story = {
  name: "Rien à offrir",
  args: { data: { ...data, offerable: [] } },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("Rien à offrir pour l'instant.")).toBeVisible();
  },
};

export const Closed: Story = {
  args: { data: null },
};
