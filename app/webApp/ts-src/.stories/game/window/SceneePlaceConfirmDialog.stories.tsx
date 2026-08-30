import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "@storybook/test";
import { SceneePlaceConfirmDialog } from "../../../game/components/SceneePlaceConfirmDialog";

const meta: Meta<typeof SceneePlaceConfirmDialog> = {
  title: "Game/Windows/SceneePlaceConfirmDialog",
  component: SceneePlaceConfirmDialog,
  parameters: { layout: "fullscreen" },
};
export default meta;

type Story = StoryObj<typeof SceneePlaceConfirmDialog>;

export const Open: Story = {
  args: {
    open: true,
    onConfirm: fn(),
    onCancel: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("Place this scene here?")).toBeVisible();
    await expect(body.getByRole("button", { name: "Yes" })).toBeVisible();
    await expect(body.getByRole("button", { name: "No" })).toBeVisible();
  },
};

export const ConfirmsOnYes: Story = {
  args: {
    open: true,
    onConfirm: fn(),
    onCancel: fn(),
  },
  play: async ({ args }) => {
    const body = within(document.body);
    await userEvent.click(body.getByRole("button", { name: "Yes" }));
    await expect(args.onConfirm).toHaveBeenCalled();
  },
};

export const Closed: Story = {
  args: {
    open: false,
    onConfirm: fn(),
    onCancel: fn(),
  },
};
