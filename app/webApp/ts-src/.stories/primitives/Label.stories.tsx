import type { Meta, StoryObj } from "@storybook/react";
import { Label } from "../../ui/primitives/Label";

const meta: Meta<typeof Label> = {
  title: "Primitives/Label",
  component: Label,
};
export default meta;

type Story = StoryObj<typeof Label>;

export const Default: Story = {
  args: { children: "Field label" },
};

export const WithInput: Story = {
  render: () => (
    <div className="flex flex-col gap-1">
      <Label htmlFor="demo">Username</Label>
      <input
        id="demo"
        className="bg-[#111] border border-[#555] rounded px-3 py-2 text-[#eee] font-mono text-sm outline-none"
        placeholder="Steve"
      />
    </div>
  ),
};
