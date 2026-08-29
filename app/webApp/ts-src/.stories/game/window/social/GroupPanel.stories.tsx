import type { Meta, StoryObj } from "@storybook/react";
import { GroupPanel } from "../../../../game/components/social/GroupPanel";
import type { GroupInfo } from "../../../../game/types";
import { stubMcState } from "../../../_support/mcState";

const group: GroupInfo = {
  id: "g1",
  leaderId: "player-1",
  leaderName: "alice",
  members: [
    { playerId: "player-1", playerName: "alice", online: true },
    { playerId: "player-2", playerName: "bob", online: true },
    { playerId: "player-3", playerName: "carol", online: false },
  ],
};

const meta: Meta<typeof GroupPanel> = {
  title: "Game/Windows/Social/GroupPanel",
  component: GroupPanel,
  parameters: { layout: "fullscreen" },
  decorators: [stubMcState()],
  args: { open: true, myPlayerId: "player-1", onClose: () => {} },
};
export default meta;

type Story = StoryObj<typeof GroupPanel>;

export const AsLeader: Story = { args: { group } };

export const AsMember: Story = { args: { group, myPlayerId: "player-2" } };

export const NoGroup: Story = { args: { group: null } };

export const NoGroupWithInvite: Story = {
  args: {
    group: null,
    invite: { kind: "group", id: "inv1", name: "", from: "dave" },
  },
};
