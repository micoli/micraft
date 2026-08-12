import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "@storybook/test";
import { SaveButton } from "../../primitives/SaveButton";

const meta: Meta<typeof SaveButton> = {
  title: "Primitives/SaveButton",
  component: SaveButton,
};
export default meta;

type Story = StoryObj<typeof SaveButton>;

export const Idle: Story = {
  args: { saving: false, saved: false, onClick: () => {} },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("button", { name: "Save" })).toBeVisible();
  },
};

export const Saving: Story = {
  args: { saving: true, saved: false, onClick: () => {} },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("button")).toBeDisabled();
  },
};

export const Saved: Story = {
  args: { saving: false, saved: true, onClick: () => {} },
};
