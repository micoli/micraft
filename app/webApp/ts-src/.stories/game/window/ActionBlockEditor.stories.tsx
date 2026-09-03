import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "@storybook/test";
import { ActionBlockEditor } from "../../../game/overlays/ActionBlockEditor";
import type { ActionBlockFormData } from "../../../game/types";

const base: ActionBlockFormData = {
  pos: { x: 12, y: 64, z: -7 },
  name: "actionblock-1",
  onActivate: "notify('Door opened')",
  onTargetEvent: "",
  onRemoteEvent: "getBlock('gate').remote()",
  variables: { count: "0", locked: "false" },
};

const meta: Meta<typeof ActionBlockEditor> = {
  title: "Game/Windows/ActionBlockEditor",
  component: ActionBlockEditor,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => {
      if (!window.mcState) {
        (window as unknown as { mcState: unknown }).mcState = { events: [], modalOpen: false };
      }
      return <Story />;
    },
  ],
};
export default meta;

type Story = StoryObj<typeof ActionBlockEditor>;

export const Open: Story = {
  args: { data: base, onClose: fn() },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("ACTION BLOCK")).toBeVisible();
    await expect(body.getByText("12, 64, -7")).toBeVisible();
    await expect(body.getByText("count")).toBeVisible();
    await expect(body.getByText("locked")).toBeVisible();
  },
};

export const WithNameError: Story = {
  args: {
    data: { ...base, error: "The name 'gate' is already used by another action block." },
    onClose: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/already used/)).toBeVisible();
  },
};

export const AddsAVariable: Story = {
  args: { data: { ...base, variables: {} }, onClose: fn() },
  play: async () => {
    const body = within(document.body);
    await userEvent.type(body.getByPlaceholderText("key"), "speed");
    await userEvent.type(body.getByPlaceholderText("value"), "5");
    await userEvent.click(body.getByRole("button", { name: "+" }));
    await expect(body.getByText("speed")).toBeVisible();
  },
};

export const CancelCloses: Story = {
  args: { data: base, onClose: fn() },
  play: async ({ args }) => {
    const body = within(document.body);
    await userEvent.click(body.getByRole("button", { name: "Cancel" }));
    await expect(args.onClose).toHaveBeenCalled();
  },
};

export const DeleteCloses: Story = {
  args: { data: base, onClose: fn() },
  play: async ({ args }) => {
    const body = within(document.body);
    await userEvent.click(body.getByRole("button", { name: "Delete" }));
    await expect(args.onClose).toHaveBeenCalled();
  },
};
