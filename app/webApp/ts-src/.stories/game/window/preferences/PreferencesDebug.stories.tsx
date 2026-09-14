import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { DebugTab } from "../../../../game/components/preferences/DebugTab";
import { usePreferences } from "../../../../game/hooks/usePreferences";
import { stubMcState } from "../../../_support/mcState";
import { preferences } from "../../../_support/preferencesFixture";

const meta: Meta<typeof DebugTab> = {
  title: "Game/Windows/Preferences/Debug",
  component: DebugTab,
  decorators: [stubMcState()],
  render: () => {
    const pref = usePreferences({
      open: true,
      preferences,
      initialTab: "debug",
      onSave: fn(),
      onClose: fn(),
    });
    return <DebugTab pref={pref} />;
  },
};
export default meta;

type Story = StoryObj<typeof DebugTab>;

export const Debug: Story = {};
