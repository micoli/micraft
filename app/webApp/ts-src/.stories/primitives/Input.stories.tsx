import type { Meta, StoryObj } from "@storybook/react";
import { Input } from "../../game/primitives/Input";
import { Label } from "../../game/primitives/Label";

const meta: Meta<typeof Input> = {
  title: "Primitives/Input",
  component: Input,
  argTypes: {
    placeholder: { control: "text" },
    disabled: { control: "boolean" },
    type: { control: "select", options: ["text", "password", "email"] },
  },
};
export default meta;

type Story = StoryObj<typeof Input>;

export const Default: Story = {
  args: { placeholder: "Enter value..." },
};

export const WithLabel: Story = {
  render: () => (
    <div className="w-64">
      <Label htmlFor="email">Email</Label>
      <Input id="email" type="email" placeholder="player@example.com" />
    </div>
  ),
};

export const Password: Story = {
  args: { type: "password", placeholder: "Password" },
};

export const Disabled: Story = {
  args: { placeholder: "Disabled", disabled: true, value: "readonly value" },
};

export const Filled: Story = {
  args: { value: "MiCraft Player", onChange: () => {} },
};
