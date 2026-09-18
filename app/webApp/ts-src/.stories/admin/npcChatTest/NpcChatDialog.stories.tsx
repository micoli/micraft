import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import { NpcChatDialog, type NpcChatTestMessage } from "../../../admin/pages/npcChatTest/NpcChatDialog";
import { translate } from "../../../admin/i18n";

const t = (key: Parameters<typeof translate>[1], ...args: (string | number)[]) => translate("fr", key, ...args);

const messages: NpcChatTestMessage[] = [
  { role: "user", text: "Bonjour, qui es-tu ?" },
  { role: "npc", text: "Je suis l'ermite de la clairière, jeune voyageur." },
  { role: "user", text: "As-tu du travail pour moi ?" },
  {
    role: "npc",
    text: "En effet, j'aurais besoin de silex pour réparer mes outils.",
    action: { type: "offer_quest", questId: "q_fetch_flint" },
    questOffer: { id: "q_fetch_flint", title: "Collecte de silex", description: "Rapporte du silex.", level: 1 },
  },
];

const meta: Meta<typeof NpcChatDialog> = {
  title: "Admin/NpcChatTest/NpcChatDialog",
  component: NpcChatDialog,
  parameters: { layout: "padded" },
  args: { onSend: fn(), sending: false, error: null, t },
  decorators: [
    (Story) => (
      <div style={{ height: 480 }}>
        <Story />
      </div>
    ),
  ],
};
export default meta;

type Story = StoryObj<typeof NpcChatDialog>;

export const Default: Story = {
  args: { npcName: "Hermit (hermit_man)", messages },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("Hermit (hermit_man)")).toBeVisible();
    await expect(body.getByText(/silex/)).toBeVisible();
    await expect(body.getByText(/quête proposée/)).toBeVisible();
  },
};

export const Empty: Story = {
  args: { npcName: "Hermit (hermit_man)", messages: [] },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("Aucun message pour l'instant — dis bonjour.")).toBeVisible();
  },
};

export const Sending: Story = {
  args: { npcName: "Hermit (hermit_man)", messages: [{ role: "user", text: "Bonjour" }], sending: true },
};

export const WithError: Story = {
  args: {
    npcName: "Hermit (hermit_man)",
    messages: [{ role: "user", text: "Bonjour" }],
    error: "Échec du test de chat — PNJ indisponible ou LLM injoignable",
  },
};
