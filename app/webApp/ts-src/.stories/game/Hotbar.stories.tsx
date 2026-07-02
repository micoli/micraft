import type { Meta, StoryObj } from "@storybook/react";
import { Hotbar } from "../../ui/game/Hotbar";

const meta: Meta<typeof Hotbar> = {
  title: "Game/Hotbar",
  component: Hotbar,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <div className="relative w-full h-40 bg-gradient-to-b from-sky-900 to-green-900">
        <Story />
      </div>
    ),
  ],
};
export default meta;

type Story = StoryObj<typeof Hotbar>;

export const Empty: Story = {
  args: {
    inventory: {},
    visible: true,
  },
};

export const WithItems: Story = {
  args: {
    inventory: { COBBLESTONE: 64, DIRT: 12, SAND: 8, GRAVEL: 3 },
    visible: true,
  },
};

export const FullInventory: Story = {
  args: {
    inventory: {
      COBBLESTONE: 64,
      DIRT: 64,
      SAND: 32,
      GRAVEL: 16,
      SANDSTONE: 8,
      SNOWBALL: 5,
      FLINT: 1,
    },
    visible: true,
  },
};

export const Hidden: Story = {
  args: {
    inventory: { COBBLESTONE: 64, DIRT: 12 },
    visible: false,
  },
};
