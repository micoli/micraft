import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "@storybook/test";
import { Inventory } from "../../game/components/Inventory";

const meta: Meta<typeof Inventory> = {
  title: "Game/Inventory",
  component: Inventory,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => {
      if (!window.mcState) {
        (window as unknown as { mcState: unknown }).mcState = { events: [], playerName: "alice" };
      }
      return (
        <div className="relative w-full h-screen bg-gradient-to-b from-slate-900 to-slate-800">
          <Story />
        </div>
      );
    },
  ],
};
export default meta;

type Story = StoryObj<typeof Inventory>;

const itemMeta = {
  COBBLESTONE: { label: "Cobblestone", bg: "#808080" },
  DIRT: { label: "Dirt", bg: "#8B4513" },
  SAND: { label: "Sand", bg: "#C2B280" },
  GRAVEL: { label: "Gravel", bg: "#9E9E9E" },
  SNOWBALL: { label: "Snowball", bg: "#E0F0FF" },
  FLINT: { label: "Flint", bg: "#444" },
};

const inventory = {
  COBBLESTONE: 64,
  DIRT: 32,
  SAND: 16,
  GRAVEL: 8,
  SNOWBALL: 3,
  FLINT: 1,
};

export const WithItems: Story = {
  args: { inventory, itemMeta, visible: true },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByPlaceholderText(/Filtrer/i)).toBeVisible();
    await expect(body.getByText("Cobblestone")).toBeVisible();
  },
};

export const Empty: Story = {
  args: { inventory: {}, itemMeta: {}, visible: true },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/Inventory empty/i)).toBeVisible();
  },
};

export const WithWallet: Story = {
  args: { inventory, itemMeta, visible: true, wallet: 253 },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/2g/)).toBeVisible();
  },
};

export const Hidden: Story = {
  args: { inventory, itemMeta, visible: false },
};
