import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { Dialog, DialogContent, DialogTitle, DialogTrigger, DialogClose } from "../../game/primitives/Dialog";
import { Button } from "../../game/primitives/Button";

const meta: Meta = {
  title: "Primitives/Dialog",
  parameters: { layout: "centered" },
};
export default meta;

type Story = StoryObj;

export const BasicDialog: Story = {
  render: () => (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="primary">Open Dialog</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogTitle>Confirm action</DialogTitle>
        <p className="text-white/60 text-sm mt-2 mb-4">Are you sure you want to disconnect?</p>
        <div className="flex gap-2 justify-end">
          <DialogClose asChild>
            <Button variant="secondary" size="sm">
              Cancel
            </Button>
          </DialogClose>
          <DialogClose asChild>
            <Button variant="danger" size="sm">
              Disconnect
            </Button>
          </DialogClose>
        </div>
      </DialogContent>
    </Dialog>
  ),
};

export const PauseStyleDialog: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    return (
      <div>
        <Button onClick={() => setOpen(true)}>Open Pause Menu</Button>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogContent className="min-w-[220px] p-8 flex flex-col gap-3">
            <DialogTitle className="text-center font-mono text-xl tracking-[0.25em] mb-2">PAUSE</DialogTitle>
            <Button variant="secondary" className="font-mono">
              Preferences
            </Button>
            <Button variant="secondary" className="font-mono">
              Character
            </Button>
            <Button variant="danger" onClick={() => setOpen(false)} className="font-mono">
              Disconnect
            </Button>
          </DialogContent>
        </Dialog>
      </div>
    );
  },
};
