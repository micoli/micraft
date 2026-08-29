import type React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { XpBar } from "../../../game/components/XpBar";
import { GameContext, GameContextValue } from "../../../game/GameContext";
import type { UiState } from "../../../game/UIReducer";

function withXpState(xpState: UiState["xpState"]) {
  const ctx = { state: { xpState } as UiState } as GameContextValue;
  return function WithXpState(Story: () => React.ReactNode) {
    return <GameContext.Provider value={ctx}>{Story()}</GameContext.Provider>;
  };
}

const meta: Meta<typeof XpBar> = {
  title: "Game/Layout/XpBar",
  component: XpBar,
  parameters: { layout: "centered" },
};
export default meta;

type Story = StoryObj<typeof XpBar>;

export const MidLevel: Story = {
  decorators: [withXpState({ xpGained: 0, totalXp: 320, level: 4, leveledUp: false, nextLevelXp: 800 })],
  args: {},
};

export const AlmostLevelUp: Story = {
  decorators: [withXpState({ xpGained: 0, totalXp: 790, level: 4, leveledUp: false, nextLevelXp: 800 })],
  args: {},
};

export const NoXpState: Story = {
  decorators: [withXpState(null)],
  args: {},
};
