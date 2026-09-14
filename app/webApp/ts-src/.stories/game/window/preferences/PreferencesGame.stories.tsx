import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { GameTab } from "../../../../game/components/preferences/GameTab";
import { usePreferences } from "../../../../game/hooks/usePreferences";
import { stubMcState } from "../../../_support/mcState";
import { preferences } from "../../../_support/preferencesFixture";

const meta: Meta<typeof GameTab> = {
  title: "Game/Windows/Preferences/Game",
  component: GameTab,
  decorators: [stubMcState()],
  render: () => {
    const pref = usePreferences({
      open: true,
      preferences,
      initialTab: "game",
      onSave: fn(),
      onClose: fn(),
    });
    return <GameTab pref={pref} />;
  },
};
export default meta;

type Story = StoryObj<typeof GameTab>;

export const Game: Story = {};
