import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { ClaimPanel } from "../../../game/components/claim/ClaimPanel";
import type { ClaimData } from "../../../game/types";
import { mockApi } from "../../_support/mockApi";

const claims: ClaimData[] = [
  {
    id: "c1",
    chunks: [
      { cx: 3, cz: 5 },
      { cx: 4, cz: 5 },
    ],
    yMin: 40,
    yMax: 90,
    ownerId: "player-1",
    ownerName: "alice",
    trustedPlayerNames: ["bob"],
  },
  {
    id: "c2",
    chunks: [{ cx: -2, cz: 0 }],
    yMin: 0,
    yMax: 128,
    ownerId: "player-9",
    ownerName: "zoe",
    trustedPlayerNames: ["alice"],
  },
];

const meta: Meta<typeof ClaimPanel> = {
  title: "Game/Windows/ClaimPanel",
  component: ClaimPanel,
  parameters: { layout: "fullscreen" },
  decorators: [mockApi({ "/api/players/names": ["alice", "bob", "carol", "dave", "zoe"] })],
  args: {
    open: true,
    myPlayerId: "player-1",
    onClose: fn(),
    onAbandon: fn(),
    onSetTrusted: fn(),
  },
};
export default meta;

type Story = StoryObj<typeof ClaimPanel>;

export const WithClaims: Story = { args: { claims } };

export const Empty: Story = { args: { claims: [] } };
