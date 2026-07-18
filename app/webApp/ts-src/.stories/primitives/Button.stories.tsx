import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "@storybook/test";
import { Button } from "../../primitives/Button";

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
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("button", { name: "Primary" })).toBeVisible();
  },
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
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const btn = canvas.getByRole("button", { name: "Disabled" });
    await expect(btn).toBeDisabled();
  },
};

export const Clickable: Story = {
  args: { children: "Click me", variant: "primary", onClick: fn() },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Click me" }));
    await expect(args.onClick).toHaveBeenCalledOnce();
  },
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
