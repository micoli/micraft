import type { Meta, StoryObj } from "@storybook/react";
import { DragMode, type dragMode } from "../../primitives/DragMode";

const meta: Meta<typeof DragMode> = {
  title: "Primitives/DragMode",
  component: DragMode,
};
export default meta;

type Story = StoryObj<typeof DragMode>;

export const Active: Story = {
  args: { m: { key: "place", label: "Place", hint: "" }, activeDragMode: "place" },
};

export const Inactive: Story = {
  args: { m: { key: "zoom", label: "Zoom", hint: "⌃" }, activeDragMode: "place" },
};

export const AllModes: Story = {
  render: () => {
    const modes: { key: dragMode; label: string; hint: string }[] = [
      { key: "place", label: "Place", hint: "" },
      { key: "zoom", label: "Zoom", hint: "⌃" },
      { key: "pan", label: "Pan", hint: "⌥" },
      { key: "rotate", label: "Rotate", hint: "⌘" },
    ];
    return (
      <div className="flex gap-2">
        {modes.map((m) => (
          <DragMode key={m.key} m={m} activeDragMode="pan" />
        ))}
      </div>
    );
  },
};
