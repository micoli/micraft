import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { BoolRow } from "../../primitives/BoolRow";

const meta: Meta<typeof BoolRow> = {
  title: "Primitives/BoolRow",
  component: BoolRow,
};
export default meta;

type Story = StoryObj<typeof BoolRow>;

export const Checked: Story = {
  args: { label: "Fly mode", value: true, onChange: () => {} },
  render: (args) => (
    <div className="w-72">
      <BoolRow {...args} />
    </div>
  ),
};

export const Unchecked: Story = {
  args: { label: "God mode", value: false, onChange: () => {} },
  render: (args) => (
    <div className="w-72">
      <BoolRow {...args} />
    </div>
  ),
};

function RowList() {
  const [flags, setFlags] = useState({ fly: true, god: false, noclip: false });
  return (
    <div className="w-72">
      {Object.entries(flags).map(([key, value]) => (
        <BoolRow key={key} label={key} value={value} onChange={(v) => setFlags((s) => ({ ...s, [key]: v }))} />
      ))}
    </div>
  );
}

export const List: Story = {
  render: () => <RowList />,
};
