import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { KeybindingsTab } from "../../../../game/components/preferences/KeybindingsTab";
import { usePreferences } from "../../../../game/hooks/usePreferences";
import { stubMcState } from "../../../_support/mcState";
import { preferences } from "../../../_support/preferencesFixture";

const meta: Meta<typeof KeybindingsTab> = {
  title: "Game/Windows/Preferences/Keybindings",
  component: KeybindingsTab,
  decorators: [stubMcState()],
  render: () => {
    const pref = usePreferences({
      open: true,
      preferences,
      initialTab: "keybindings",
      onSave: fn(),
      onClose: fn(),
    });
    return <KeybindingsTab pref={pref} />;
  },
};
export default meta;

type Story = StoryObj<typeof KeybindingsTab>;

export const Keybindings: Story = {};
