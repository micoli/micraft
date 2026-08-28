import type { Meta, StoryObj } from "@storybook/react";
import { CurrencyDisplay } from "../../game/components/auction/CurrencyDisplay";

const meta: Meta<typeof CurrencyDisplay> = {
  title: "Primitives/CurrencyDisplay",
  component: CurrencyDisplay,
};
export default meta;

type Story = StoryObj<typeof CurrencyDisplay>;

export const GoldSilverCopper: Story = {
  args: { copper: 12345 },
};

export const SilverAndCopperOnly: Story = {
  args: { copper: 47 },
};

export const GoldAndCopperOnly: Story = {
  args: { copper: 1107 },
};

export const CopperOnly: Story = {
  args: { copper: 7 },
};

export const Zero: Story = {
  args: { copper: 0 },
};
