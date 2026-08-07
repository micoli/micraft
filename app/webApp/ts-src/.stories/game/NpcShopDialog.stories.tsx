import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "@storybook/test";
import { NpcShopDialog } from "../../game/npc/NpcShopDialog";
import type { NpcDialogData } from "../../game/types";

const meta: Meta<typeof NpcShopDialog> = {
  title: "Game/NpcShopDialog",
  component: NpcShopDialog,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <div className="relative w-full h-screen bg-gradient-to-b from-slate-900 to-slate-800">
        <Story />
      </div>
    ),
  ],
};
export default meta;

type Story = StoryObj<typeof NpcShopDialog>;

const itemMeta = {
  COBBLESTONE: { label: "Cobblestone", bg: "#808080" },
  DIRT: { label: "Dirt", bg: "#8B4513" },
  SAND: { label: "Sand", bg: "#C2B280" },
  GRAVEL: { label: "Gravel", bg: "#9E9E9E" },
  FLINT: { label: "Flint", bg: "#444" },
};

const shopData: NpcDialogData = {
  type: "seller",
  name: "Marchand Pierre",
  npcId: "npc-merchant-1",
  shopItems: [
    { itemType: "COBBLESTONE", buyPrice: 2, sellPrice: 1 },
    { itemType: "SAND", buyPrice: 3, sellPrice: 1 },
    { itemType: "GRAVEL", buyPrice: 2, sellPrice: 1 },
    { itemType: "FLINT", buyPrice: 5, sellPrice: 2 },
  ],
};

const playerInventory = {
  COBBLESTONE: 32,
  DIRT: 16,
};

export const Basic: Story = {
  args: {
    data: shopData,
    wallet: 100,
    itemMeta,
    inventory: playerInventory,
    onClose: fn(),
    onBuy: fn(),
    onSell: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("Marchand Pierre")).toBeVisible();
    await expect(body.getByText("Boutique")).toBeVisible();
    await expect(body.getByText("Mon inventaire")).toBeVisible();
  },
};

export const RichWallet: Story = {
  args: {
    data: shopData,
    wallet: 999,
    itemMeta,
    inventory: playerInventory,
    onClose: fn(),
    onBuy: fn(),
    onSell: fn(),
  },
};

export const PoorPlayer: Story = {
  args: {
    data: shopData,
    wallet: 0,
    itemMeta,
    inventory: playerInventory,
    onClose: fn(),
    onBuy: fn(),
    onSell: fn(),
  },
};

export const EmptyInventory: Story = {
  args: {
    data: shopData,
    wallet: 50,
    itemMeta,
    inventory: {},
    onClose: fn(),
    onBuy: fn(),
    onSell: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/Inventaire vide/i)).toBeVisible();
  },
};

export const Null: Story = {
  args: {
    data: null,
    wallet: 0,
    itemMeta,
    inventory: {},
    onClose: fn(),
    onBuy: fn(),
    onSell: fn(),
  },
};
