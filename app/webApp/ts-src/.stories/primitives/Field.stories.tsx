import type { Meta, StoryObj } from "@storybook/react";
import { Field } from "../../primitives/Field";
import { Input } from "../../primitives/Input";

const meta: Meta<typeof Field> = {
  title: "Primitives/Field",
  component: Field,
};
export default meta;

type Story = StoryObj<typeof Field>;

export const WithInput: Story = {
  render: () => (
    <div className="w-64">
      <Field label="Display name" htmlFor="displayName">
        <Input id="displayName" placeholder="Steve" />
      </Field>
    </div>
  ),
};

export const WithText: Story = {
  render: () => (
    <div className="w-64">
      <Field label="World generator">
        <p className="text-sm text-white">Perlin</p>
      </Field>
    </div>
  ),
};
