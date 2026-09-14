import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { Preferences } from "../../../game/components/preferences/Preferences";
import { stubMcState } from "../../_support/mcState";
import { preferences } from "../../_support/preferencesFixture";

const meta: Meta<typeof Preferences> = {
  title: "Game/Windows/Preferences",
  component: Preferences,
  parameters: { layout: "fullscreen" },
  decorators: [stubMcState()],
  args: {
    open: true,
    preferences,
    fullMeshedChunks: 128,
    impostorMeshedChunks: 12,
    onSave: fn(),
    onClose: fn(),
    onLiveOverride: fn(),
  },
};
export default meta;

type Story = StoryObj<typeof Preferences>;

export const PreferenceWindow: Story = { args: { initialTab: "chat" } };
export const Closed: Story = { args: { open: false } };
