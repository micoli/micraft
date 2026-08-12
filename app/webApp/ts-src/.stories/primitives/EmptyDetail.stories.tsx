import type { Meta, StoryObj } from "@storybook/react";
import { EmptyDetail } from "../../primitives/EmptyDetail";

const meta: Meta<typeof EmptyDetail> = {
  title: "Primitives/EmptyDetail",
  component: EmptyDetail,
  parameters: { layout: "fullscreen" },
};
export default meta;

type Story = StoryObj<typeof EmptyDetail>;

export const Default: Story = {
  args: { message: "Select an item to see details" },
  render: (args) => (
    <div className="h-64 flex bg-black/40">
      <EmptyDetail {...args} />
    </div>
  ),
};
