import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { CurrencyInput } from "../../game/components/auction/CurrencyInput";
import { CurrencyDisplay } from "../../game/components/auction/CurrencyDisplay";

const meta: Meta<typeof CurrencyInput> = {
  title: "Primitives/CurrencyInput",
  component: CurrencyInput,
};
export default meta;

type Story = StoryObj<typeof CurrencyInput>;

export const Empty: Story = {
  args: { onChange: fn() },
};

export const WithInitialValue: Story = {
  args: { initialCopper: 12345, onChange: fn() },
};

function InteractiveDemo() {
  const [copper, setCopper] = useState<number | null>(null);
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <CurrencyInput onChange={setCopper} />
      <div style={{ color: "#fff", fontSize: 13 }}>
        Total:{" "}
        {copper === null ? (
          <span style={{ color: "rgba(255,255,255,0.4)" }}>—</span>
        ) : (
          <CurrencyDisplay copper={copper} />
        )}
      </div>
    </div>
  );
}

export const Interactive: Story = {
  render: () => <InteractiveDemo />,
};
