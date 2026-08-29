import type { Meta, StoryObj } from "@storybook/react";
import { fn, userEvent, within, expect } from "@storybook/test";
import { MenuGrid } from "../../primitives/MenuGrid";

const meta: Meta<typeof MenuGrid> = {
  title: "Primitives/MenuGrid",
  component: MenuGrid,
};
export default meta;

type Story = StoryObj<typeof MenuGrid>;

const items = [
  { label: "Resume", icon: "▶", onClick: fn() },
  { label: "Character", icon: "🧍", onClick: fn() },
  { label: "Preferences", icon: "⚙", onClick: fn() },
  { label: "Auction House", icon: "💰", onClick: fn() },
  { label: "Disconnect", icon: "⏻", variant: "danger" as const, onClick: fn() },
  { label: "Locked", onClick: fn(), disabled: true },
];

export const Default: Story = {
  args: { items, className: "w-[420px]" },
};

export const ThreeColumns: Story = {
  args: { items, columns: 3, className: "w-[560px]" },
};

export const Clickable: Story = {
  args: { items: [{ label: "Click me", onClick: fn() }] },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Click me" }));
    await expect(args.items[0].onClick).toHaveBeenCalledOnce();
  },
};
