import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "@storybook/test";
import { PauseMenu } from "../../game/overlays/PauseMenu";
import { Button } from "../../primitives/Button";

const meta: Meta<typeof PauseMenu> = {
  title: "Overlays/PauseMenu",
  component: PauseMenu,
  parameters: { layout: "fullscreen" },
};
export default meta;

type Story = StoryObj<typeof PauseMenu>;

const onDisconnect = fn();
const onClose = fn();

export const Open: Story = {
  args: {
    open: true,
    onClose,
    items: [
      { label: "Preferences", callback: fn() },
      { label: "Character", callback: fn() },
      { label: "Macros", callback: fn() },
      { label: "Disconnect", variant: "danger", callback: onDisconnect },
    ],
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("PAUSE")).toBeVisible();
    await expect(body.getByRole("button", { name: "Disconnect" })).toBeVisible();
  },
};

export const ClickDisconnect: Story = {
  args: {
    open: true,
    onClose,
    items: [
      { label: "Preferences", callback: fn() },
      { label: "Disconnect", variant: "danger", callback: onDisconnect },
    ],
  },
  play: async ({ args }) => {
    const body = within(document.body);
    const items = args.items ?? [];
    const disconnectItem = items.find((i) => i.label === "Disconnect");
    await userEvent.click(body.getByRole("button", { name: "Disconnect" }));
    await expect(disconnectItem?.callback).toHaveBeenCalledOnce();
  },
};

function ControlledStory() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <div className="absolute inset-0 flex items-center justify-center">
        <Button onClick={() => setOpen(true)}>Open Pause Menu (ESC)</Button>
      </div>
      <PauseMenu
        open={open}
        onClose={() => setOpen(false)}
        items={[
          { label: "Preferences", callback: () => setOpen(false) },
          { label: "Character", callback: () => setOpen(false) },
          { label: "Macros", callback: () => setOpen(false) },
          { label: "Disconnect", variant: "danger", callback: () => setOpen(false) },
        ]}
      />
    </>
  );
}

export const Controlled: Story = {
  render: () => <ControlledStory />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: /open pause menu/i }));
    const body = within(document.body);
    await expect(body.getByText("PAUSE")).toBeVisible();
  },
};
