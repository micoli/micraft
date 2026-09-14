import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { ChatTab } from "../../../../game/components/preferences/ChatTab";
import { usePreferences } from "../../../../game/hooks/usePreferences";
import { stubMcState } from "../../../_support/mcState";
import { preferences } from "../../../_support/preferencesFixture";

const meta: Meta<typeof ChatTab> = {
  title: "Game/Windows/Preferences/Chat",
  component: ChatTab,
  decorators: [stubMcState()],
  render: () => {
    const pref = usePreferences({
      open: true,
      preferences,
      initialTab: "chat",
      onSave: fn(),
      onClose: fn(),
    });
    return <ChatTab pref={pref} knownChannels={preferences.knownChannels} />;
  },
};
export default meta;

type Story = StoryObj<typeof ChatTab>;

export const Chat: Story = {};
