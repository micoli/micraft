import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "@storybook/test";
import { RemovableBadge } from "../../primitives/RemovableBadge";

const meta: Meta<typeof RemovableBadge> = {
  title: "Primitives/RemovableBadge",
  component: RemovableBadge,
};
export default meta;

type Story = StoryObj<typeof RemovableBadge>;

export const Default: Story = {
  args: { name: "Bob", onRemove: fn() },
};

export const CustomColor: Story = {
  args: { name: "Alice", color: "bg-[#3C50E0]/20 text-[#3C50E0]", onRemove: fn() },
};

export const Removable: Story = {
  args: { name: "Bob", onRemove: fn() },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Remove Bob" }));
    await expect(args.onRemove).toHaveBeenCalledOnce();
  },
};

export const List: Story = {
  render: () => (
    <div className="flex flex-wrap gap-1.5">
      {["Alice", "Bob", "Carol"].map((name) => (
        <RemovableBadge key={name} name={name} onRemove={() => {}} />
      ))}
    </div>
  ),
};
