import type { Meta, StoryObj } from "@storybook/react";
import { FormField } from "../../primitives/FormField";
import { Label } from "../../primitives/Label";
import { Input } from "../../primitives/Input";

const meta: Meta<typeof FormField> = {
  title: "Primitives/FormField",
  component: FormField,
};
export default meta;

type Story = StoryObj<typeof FormField>;

export const Default: Story = {
  render: () => (
    <div className="w-64">
      <FormField>
        <Label htmlFor="email">Email</Label>
        <Input id="email" type="email" placeholder="player@example.com" />
      </FormField>
    </div>
  ),
};

export const Stacked: Story = {
  render: () => (
    <div className="w-64 flex flex-col gap-4">
      <FormField>
        <Label htmlFor="email2">Email</Label>
        <Input id="email2" type="email" placeholder="player@example.com" />
      </FormField>
      <FormField>
        <Label htmlFor="pass2">Password</Label>
        <Input id="pass2" type="password" placeholder="••••••••" />
      </FormField>
    </div>
  ),
};
