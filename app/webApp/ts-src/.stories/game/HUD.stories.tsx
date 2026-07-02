import type { Meta, StoryObj } from "@storybook/react";
import { HUD } from "../../ui/game/HUD";
import type { HudData } from "../../ui/types";

const meta: Meta<typeof HUD> = {
  title: "Game/HUD",
  component: HUD,
  parameters: { layout: "fullscreen" },
  argTypes: {
    mode: { control: "select", options: ["simple", "medium", "complete"] },
  },
  decorators: [
    (Story) => (
      <div className="relative w-full h-96 bg-gradient-to-b from-sky-700 to-green-800">
        <Story />
      </div>
    ),
  ],
};
export default meta;

type Story = StoryObj<typeof HUD>;

const baseData: HudData = {
  x: 128.4,
  y: 64.0,
  z: -256.7,
  yaw: 45.2,
  pitch: -12.0,
  stance: "standing",
  speed: 1.0,
  fps: 60,
  kbIn: 12.4,
  kbOut: 0.8,
  biome: "FOREST",
  targetBlock: "GRASS at (128, 63, -257)",
  gameTime: "14:32",
  reconcileXzStats: "0/60",
  reconcileYStats: "0/60",
  tickDtMs: 50.1,
  tickJitterMs: 2.3,
  tickDtMinMs: 48.5,
  tickDtMaxMs: 53.2,
  tickJitterMinMs: 0.5,
  tickJitterMaxMs: 8.1,
  chunkDownloading: 0,
  chunkMeshing: 2,
};

export const Simple: Story = {
  args: { data: baseData, mode: "simple" },
};

export const Medium: Story = {
  args: { data: baseData, mode: "medium" },
};

export const Complete: Story = {
  args: { data: baseData, mode: "complete" },
};

export const NoData: Story = {
  args: { data: null, mode: "simple" },
};

export const Sneaking: Story = {
  args: { data: { ...baseData, stance: "sneaking", speed: 0.3 }, mode: "medium" },
};
