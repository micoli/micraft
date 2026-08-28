import type { Meta, StoryObj } from "@storybook/react";
import { Notifications } from "../../game/components/Notifications";

const meta: Meta<typeof Notifications> = {
  title: "Game/Notifications",
  component: Notifications,
  parameters: { layout: "fullscreen" },
};
export default meta;

type Story = StoryObj<typeof Notifications>;

export const None: Story = {
  args: { notif: null },
};

export const BlockBroken: Story = {
  args: { notif: { msg: "COBBLESTONE broken", key: 1 } },
};

export const ItemPickup: Story = {
  args: { notif: { msg: "Picked up: DIRT ×4", key: 2 } },
};

export const ServerMessage: Story = {
  args: { notif: { msg: "Player Steve joined the game", key: 3 } },
};
