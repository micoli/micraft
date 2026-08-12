import type { Meta, StoryObj } from "@storybook/react";
import { Icon } from "../../primitives/Icon";
import { ICONS } from "../../primitives/icons";

const meta: Meta<typeof Icon> = {
  title: "Primitives/Icon",
  component: Icon,
  argTypes: {
    size: { control: "number" },
  },
};
export default meta;

type Story = StoryObj<typeof Icon>;

export const Default: Story = {
  args: { d: ICONS.status, size: 18 },
};

export const Large: Story = {
  args: { d: ICONS.config, size: 40 },
};

export const AllIcons: Story = {
  render: () => (
    <div className="grid grid-cols-6 gap-4 text-white">
      {Object.entries(ICONS).map(([name, d]) => (
        <div key={name} className="flex flex-col items-center gap-1">
          <Icon d={d} size={24} />
          <span className="text-[10px] text-white/50">{name}</span>
        </div>
      ))}
    </div>
  ),
};
