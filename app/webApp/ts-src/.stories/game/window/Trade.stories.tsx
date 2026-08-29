import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Trade } from "../../../game/components/trade/Trade";

const itemMeta = {
  COBBLESTONE: { label: "Cobblestone", bg: "#808080" },
  DIRT: { label: "Dirt", bg: "#8B4513" },
  SAND: { label: "Sand", bg: "#C2B280" },
  FLINT: { label: "Flint", bg: "#444" },
};

const inventory = { COBBLESTONE: 32, DIRT: 16, FLINT: 4 };

const meta: Meta<typeof Trade> = {
  title: "Game/Windows/Trade",
  component: Trade,
  parameters: { layout: "fullscreen" },
  args: {
    open: true,
    tradeId: "t1",
    otherPlayer: "bob",
    myOffer: {},
    theirOffer: {},
    myAccepted: false,
    theirAccepted: false,
    inventory,
    itemMeta,
    onClose: fn(),
    onAccept: fn(),
    onOffer: fn(),
  },
};
export default meta;

type Story = StoryObj<typeof Trade>;

export const Fresh: Story = {};

export const TheyOffered: Story = {
  args: { theirOffer: { SAND: 10, FLINT: 2 }, theirAccepted: true },
};

export const BothAccepted: Story = {
  args: { theirOffer: { SAND: 10 }, myAccepted: true, theirAccepted: true },
};

export const EmptyInventory: Story = {
  args: { inventory: {} },
};
