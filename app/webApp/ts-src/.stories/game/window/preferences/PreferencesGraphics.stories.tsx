import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { GraphicsTab } from "../../../../game/components/preferences/GraphicsTab";
import { usePreferences } from "../../../../game/hooks/usePreferences";
import { stubMcState } from "../../../_support/mcState";
import { preferences } from "../../../_support/preferencesFixture";

const meta: Meta<typeof GraphicsTab> = {
  title: "Game/Windows/Preferences/Graphics",
  component: GraphicsTab,
  decorators: [stubMcState()],
  render: () => {
    const pref = usePreferences({
      open: true,
      preferences,
      initialTab: "graphics",
      onSave: fn(),
      onClose: fn(),
    });
    return <GraphicsTab pref={pref} fullMeshedChunks={128} impostorMeshedChunks={12} />;
  },
};
export default meta;

type Story = StoryObj<typeof GraphicsTab>;

export const Graphics: Story = {};
