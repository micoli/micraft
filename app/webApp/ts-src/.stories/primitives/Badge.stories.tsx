import type { Meta, StoryObj } from "@storybook/react";
import { Badge } from "../../primitives/Badge";

const meta: Meta<typeof Badge> = {
  title: "Primitives/Badge",
  component: Badge,
};
export default meta;

type Story = StoryObj<typeof Badge>;

export const Blue: Story = {
  args: { children: "elite", color: "bg-[#3C50E0]/20 text-[#3C50E0]" },
};

export const Neutral: Story = {
  args: { children: "perlin", color: "bg-[#2E3A4E] text-[#8A99AF]" },
};

export const AllColors: Story = {
  render: () => (
    <div className="flex gap-2">
      <Badge color="bg-[#3C50E0]/20 text-[#3C50E0]">tier 1</Badge>
      <Badge color="bg-green-500/20 text-green-400">passive</Badge>
      <Badge color="bg-red-500/20 text-red-400">aggressive</Badge>
      <Badge color="bg-[#2E3A4E] text-[#8A99AF]">neutral</Badge>
    </div>
  ),
};
