import type { Meta, StoryObj } from "@storybook/react";
import { SegmentedBar } from "../../primitives/SegmentedBar";

const meta: Meta<typeof SegmentedBar> = {
  title: "Primitives/SegmentedBar",
  component: SegmentedBar,
};
export default meta;

type Story = StoryObj<typeof SegmentedBar>;

const wide = { width: "320px", height: "8px", borderRadius: "3px" } as const;

export const ChunkLoading: Story = {
  args: {
    style: wide,
    segments: [
      { value: 60, className: "bg-green-600" },
      { value: 25, className: "bg-orange-600" },
      { value: 15, className: "bg-red-900" },
    ],
  },
};

export const Empty: Story = {
  args: {
    style: wide,
    segments: [
      { value: 0, className: "bg-green-600" },
      { value: 0, className: "bg-orange-600" },
      { value: 0, className: "bg-red-900" },
    ],
  },
};

export const Full: Story = {
  args: {
    style: wide,
    segments: [{ value: 100, className: "bg-green-600" }],
  },
};

export const CustomColors: Story = {
  args: {
    style: wide,
    segments: [
      { value: 1, color: "#3C50E0" },
      { value: 1, color: "#8A99AF" },
      { value: 2, color: "#1B2436" },
    ],
  },
};
