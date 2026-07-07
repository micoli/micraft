import type { Meta, StoryObj } from "@storybook/react";
import { Button } from "../../game/primitives/Button";

const meta: Meta<typeof Button> = {
  title: "Primitives/Button",
  component: Button,
  argTypes: {
    variant: {
      control: "select",
      options: ["primary", "secondary", "danger", "ghost", "blue", "outline"],
    },
    size: {
      control: "select",
      options: ["sm", "md", "lg"],
    },
    disabled: { control: "boolean" },
  },
};
export default meta;

type Story = StoryObj<typeof Button>;

export const Primary: Story = {
  args: { children: "Primary", variant: "primary", size: "md" },
};

export const Secondary: Story = {
  args: { children: "Secondary", variant: "secondary", size: "md" },
};

export const Danger: Story = {
  args: { children: "Disconnect", variant: "danger", size: "md" },
};

export const Ghost: Story = {
  args: { children: "Ghost", variant: "ghost", size: "md" },
};

export const Blue: Story = {
  args: { children: "Confirm", variant: "blue", size: "md" },
};

export const Outline: Story = {
  args: { children: "Outline", variant: "outline", size: "md" },
};

export const Disabled: Story = {
  args: { children: "Disabled", variant: "primary", size: "md", disabled: true },
};

export const AllVariants: Story = {
  render: () => (
    <div className="flex flex-wrap gap-3">
      {(["primary", "secondary", "danger", "ghost", "blue", "outline"] as const).map((v) => (
        <Button key={v} variant={v}>
          {v}
        </Button>
      ))}
    </div>
  ),
};

export const AllSizes: Story = {
  render: () => (
    <div className="flex items-center gap-3">
      <Button size="sm">Small</Button>
      <Button size="md">Medium</Button>
      <Button size="lg">Large</Button>
    </div>
  ),
};
