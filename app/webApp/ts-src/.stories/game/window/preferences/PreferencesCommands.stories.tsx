import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { CommandsTab } from "../../../../game/components/preferences/CommandsTab";
import { usePreferences } from "../../../../game/hooks/usePreferences";
import { stubMcState } from "../../../_support/mcState";
import { preferences } from "../../../_support/preferencesFixture";

const meta: Meta<typeof CommandsTab> = {
  title: "Game/Windows/Preferences/Commands",
  component: CommandsTab,
  decorators: [stubMcState()],
  render: () => {
    const pref = usePreferences({
      open: true,
      preferences,
      initialTab: "commands",
      onSave: fn(),
      onClose: fn(),
    });
    return <CommandsTab pref={pref} />;
  },
};
export default meta;

type Story = StoryObj<typeof CommandsTab>;

export const Commands: Story = {};
