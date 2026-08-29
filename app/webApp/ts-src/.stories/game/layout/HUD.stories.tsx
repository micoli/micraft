import type { Meta, StoryObj } from "@storybook/react";
import { HUD } from "../../../game/components/hud/HUD";
import type { HudData } from "../../../game/types";

const meta: Meta<typeof HUD> = {
  title: "Game/Layout/HUD",
  component: HUD,
  parameters: { layout: "fullscreen" },
  argTypes: {},
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
  fpsMax: 0,
  fpsMin: 0,
  gpuUploadMsAvg: 0,
  gpuUploadMsMin: 0,
  gpuUploadMsMax: 0,
  meshDrainMsMax: 0,
  meshDrainMsMin: 0,
  weather: "rain",
  fullMeshedChunks: 100,
  impostorMeshedChunks: 5,
  zoneLevel: 5,
  meshDrainMsAvg: 4,
  wsDecodeMsAvg: 1,
};

export const Simple: Story = {
  args: { data: baseData },
};

export const NoData: Story = {
  args: { data: null },
};

export const Sneaking: Story = {
  args: { data: { ...baseData, stance: "sneaking", speed: 0.3 } },
};
