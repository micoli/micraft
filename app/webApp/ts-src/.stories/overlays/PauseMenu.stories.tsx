import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { PauseMenu } from "../../game/overlays/PauseMenu";
import { Button } from "../../game/primitives/Button";

const meta: Meta<typeof PauseMenu> = {
  title: "Overlays/PauseMenu",
  component: PauseMenu,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <div className="relative w-full h-screen bg-gradient-to-b from-sky-900 to-green-900">
        <Story />
      </div>
    ),
  ],
};
export default meta;

type Story = StoryObj<typeof PauseMenu>;

export const Open: Story = {
  args: {
    open: true,
    onClose: () => {},
    onDisconnect: () => alert("disconnect"),
    onPreferences: () => alert("preferences"),
    onCharacter: () => alert("character"),
  },
};

export const Controlled: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    return (
      <>
        <div className="absolute inset-0 flex items-center justify-center">
          <Button onClick={() => setOpen(true)}>Open Pause Menu (ESC)</Button>
        </div>
        <PauseMenu
          open={open}
          onClose={() => setOpen(false)}
          onDisconnect={() => setOpen(false)}
          onPreferences={() => setOpen(false)}
          onCharacter={() => setOpen(false)}
          onMacros={() => setOpen(false)}
        />
      </>
    );
  },
};
