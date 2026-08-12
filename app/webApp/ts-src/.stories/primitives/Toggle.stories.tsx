import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "@storybook/test";
import { Toggle } from "../../primitives/Toggle";

const meta: Meta<typeof Toggle> = {
  title: "Primitives/Toggle",
  component: Toggle,
};
export default meta;

type Story = StoryObj<typeof Toggle>;

export const On: Story = {
  args: { value: true, onChange: () => {} },
};

export const Off: Story = {
  args: { value: false, onChange: () => {} },
};

function InteractiveToggle() {
  const [value, setValue] = useState(false);
  return <Toggle value={value} onChange={setValue} />;
}

export const Interactive: Story = {
  render: () => <InteractiveToggle />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const btn = canvas.getByRole("button");
    await userEvent.click(btn);
    await expect(btn).toBeVisible();
  },
};
