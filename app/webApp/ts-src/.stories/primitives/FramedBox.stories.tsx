import type { Meta, StoryObj } from "@storybook/react";
import { FramedBox } from "../../primitives/FramedBox";

const meta: Meta<typeof FramedBox> = {
  title: "Primitives/FramedBox",
  component: FramedBox,
  argTypes: {
    variant: { control: "select", options: ["default", "danger"] },
  },
};
export default meta;

type Story = StoryObj<typeof FramedBox>;

const box = { width: "160px", height: "160px", background: "#1B2436" } as const;

export const Default: Story = {
  args: { variant: "default", style: box },
};

export const Danger: Story = {
  args: { variant: "danger", style: box },
};

export const Both: Story = {
  render: () => (
    <div className="flex gap-6">
      <FramedBox variant="default" style={box} />
      <FramedBox variant="danger" style={box} />
    </div>
  ),
};
