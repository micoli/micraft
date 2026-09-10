import type React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { Compass } from "../../../game/components/Compass";
import { GameContext, GameContextValue } from "../../../game/GameContext";
import type { UiState } from "../../../game/UIReducer";
import type { HudData } from "../../../game/types";

type Target = UiState["compassTarget"];

const hudAt = (x: number, y: number, z: number, yaw: number): HudData => ({ x, y, z, yaw }) as HudData;

function withCompass(hud: HudData | null, compassTarget: Target) {
  const ctx = { state: { hud, compassTarget } as UiState } as GameContextValue;
  return function WithCompass(Story: () => React.ReactNode) {
    return <GameContext.Provider value={ctx}>{Story()}</GameContext.Provider>;
  };
}

const meta: Meta<typeof Compass> = {
  title: "Game/Layout/Compass",
  component: Compass,
  parameters: { layout: "centered" },
  args: { layoutStyle: { position: "relative", width: 160, height: 160 } },
};
export default meta;

type Story = StoryObj<typeof Compass>;

// yaw 0 faces +Z, so a target straight ahead has z > player z; a target to the right has x > player x.
export const FacingTarget: Story = {
  decorators: [withCompass(hudAt(0, 64, 0, 0), { x: 0, y: 64, z: 120, label: null })],
};

export const TargetBehind: Story = {
  decorators: [withCompass(hudAt(0, 64, 0, 0), { x: 10, y: 64, z: -120, label: null })],
};

export const TargetRight: Story = {
  decorators: [withCompass(hudAt(0, 64, 0, 0), { x: 120, y: 64, z: 5, label: null })],
};

export const WithElevationAndLabel: Story = {
  decorators: [withCompass(hudAt(0, 64, 0, 200), { x: -140, y: 190, z: 60, label: "Sommet du pic" })],
};

export const TargetBelow: Story = {
  decorators: [withCompass(hudAt(0, 120, 0, 90), { x: 40, y: 15, z: 10, label: "Caverne" })],
};

export const NoTarget: Story = {
  decorators: [withCompass(hudAt(0, 64, 0, 0), null)],
};
