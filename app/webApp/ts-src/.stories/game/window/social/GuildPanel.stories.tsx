import type { Meta, StoryObj } from "@storybook/react";
import { GuildPanel } from "../../../../game/components/social/GuildPanel";
import type { GuildInfo } from "../../../../game/types";
import { stubMcState } from "../../../_support/mcState";

const guild: GuildInfo = {
  id: "gu1",
  name: "Les Veilleurs",
  tag: "VEIL",
  motd: "Raid vendredi 21h. Soyez prêts.",
  createdAtMs: Date.UTC(2025, 0, 15),
  ownerId: "player-1",
  ranks: [
    { name: "Maître", order: 0, flags: ["INVITE", "KICK", "MANAGE_RANKS", "EDIT_MOTD", "DISBAND", "EDIT_INFO"] },
    { name: "Officier", order: 10, flags: ["INVITE", "KICK", "EDIT_MOTD"] },
    { name: "Membre", order: 50, flags: [] },
  ],
  members: [
    { playerId: "player-1", playerName: "alice", rank: "Maître", joinedAtMs: 1, online: true },
    { playerId: "player-2", playerName: "bob", rank: "Officier", joinedAtMs: 2, online: true },
    { playerId: "player-3", playerName: "carol", rank: "Membre", joinedAtMs: 3, online: false },
  ],
  bank: {},
  bankLog: [],
  myRank: "Maître",
  myFlags: ["INVITE", "KICK", "MANAGE_RANKS", "EDIT_MOTD", "DISBAND", "EDIT_INFO"],
};

const meta: Meta<typeof GuildPanel> = {
  title: "Game/Windows/Social/GuildPanel",
  component: GuildPanel,
  parameters: { layout: "fullscreen" },
  decorators: [stubMcState()],
  args: { open: true, myPlayerId: "player-1", onClose: () => {} },
};
export default meta;

type Story = StoryObj<typeof GuildPanel>;

export const AsOwner: Story = { args: { guild } };

export const AsPlainMember: Story = {
  args: { guild: { ...guild, myRank: "Membre", myFlags: [] }, myPlayerId: "player-3" },
};

export const NoGuild: Story = { args: { guild: null } };

export const NoGuildWithInvite: Story = {
  args: {
    guild: null,
    invite: { kind: "guild", id: "inv1", name: "Les Veilleurs", from: "bob" },
  },
};
