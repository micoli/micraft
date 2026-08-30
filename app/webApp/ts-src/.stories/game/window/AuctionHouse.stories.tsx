import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "@storybook/test";
import { AuctionHouse } from "../../../game/components/auction/AuctionHouse";
import { AuctionData } from "../../../game/types";
import { useState } from "react";
import { Button } from "../../../primitives/Button";

const meta: Meta<typeof AuctionHouse> = {
  title: "Game/Windows/AuctionHouse",
  component: AuctionHouse,
  parameters: { layout: "fullscreen" },
};
export default meta;

type Story = StoryObj<typeof AuctionHouse>;

const itemMeta = {
  COBBLESTONE: { label: "Cobblestone", bg: "#808080" },
  DIRT: { label: "Dirt", bg: "#8B4513" },
  SAND: { label: "Sand", bg: "#C2B280" },
  FLINT: { label: "Flint", bg: "#444" },
};

const inventory = {
  COBBLESTONE: 32,
  DIRT: 16,
  SAND: 8,
};

const auctions: AuctionData[] = [
  {
    id: "listing-1",
    sellerId: "player-bob",
    sellerName: "Bob",
    itemType: "COBBLESTONE",
    quantity: 32,
    createdAtMs: Date.now() - 3600_000,
    expiresAtMs: Date.now() + 5 * 3600_000,
    duration: "H24",
    startingPrice: 150,
    buyNowPrice: 500,
    currentBid: 220,
    currentBidderId: "player-carol",
    currentBidderName: "Carol",
    status: "ACTIVE",
    bidHistory: [
      { bidderId: "player-carol", bidderName: "Carol", amount: 220, atMs: Date.now() - 1800_000 },
      { bidderId: "player-dave", bidderName: "Dave", amount: 180, atMs: Date.now() - 3000_000 },
    ],
  },
  {
    id: "listing-2",
    sellerId: "player-alice",
    sellerName: "Alice",
    itemType: "SAND",
    quantity: 8,
    createdAtMs: Date.now() - 7200_000,
    expiresAtMs: Date.now() + 12 * 3600_000,
    duration: "H24",
    startingPrice: 5,
    buyNowPrice: null,
    currentBid: null,
    currentBidderId: null,
    currentBidderName: null,
    status: "ACTIVE",
    bidHistory: [],
  },
  {
    id: "listing-3",
    sellerId: "player-alice",
    sellerName: "Alice",
    itemType: "FLINT",
    quantity: 4,
    createdAtMs: Date.now() - 90000_000,
    expiresAtMs: Date.now() - 1000,
    duration: "H12",
    startingPrice: 40,
    buyNowPrice: 80,
    currentBid: null,
    currentBidderId: null,
    currentBidderName: null,
    status: "EXPIRED",
    bidHistory: [],
  },
];

export const Basic: Story = {
  args: {
    open: true,
    auctions,
    myPlayerId: "player-alice",
    inventory,
    itemMeta,
    onClose: fn(),
    onBid: fn(),
    onBuyNow: fn(),
    onCancel: fn(),
    onCreateListing: fn(),
    onFilterChange: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("Auction House")).toBeVisible();
    await expect(body.getByText("Cobblestone ×32")).toBeVisible();
    await expect(body.getByText("2g")).toBeVisible();
    await expect(body.getByText("2s")).toBeVisible();
  },
};

export const Empty: Story = {
  args: {
    open: true,
    auctions: [],
    myPlayerId: "player-alice",
    inventory,
    itemMeta,
    onClose: fn(),
    onBid: fn(),
    onBuyNow: fn(),
    onCancel: fn(),
    onCreateListing: fn(),
    onFilterChange: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/No .* match/i)).toBeVisible();
  },
};

function ControlledStory() {
  const [open, setOpen] = useState(false);
  const [myAuctions, setMyAuctions] = useState<AuctionData[]>(auctions);

  return (
    <>
      <div className="absolute inset-0 flex items-center justify-center">
        <Button onClick={() => setOpen(true)}>Open Auction House</Button>
      </div>
      <AuctionHouse
        open={open}
        auctions={myAuctions}
        myPlayerId="player-alice"
        inventory={inventory}
        itemMeta={itemMeta}
        onClose={() => setOpen(false)}
        onBid={(listingId, amount) =>
          setMyAuctions((prev) =>
            prev.map((a) =>
              a.id === listingId
                ? { ...a, currentBid: amount, currentBidderId: "player-alice", currentBidderName: "Alice" }
                : a,
            ),
          )
        }
        onBuyNow={(listingId) =>
          setMyAuctions((prev) => prev.map((a) => (a.id === listingId ? { ...a, status: "SOLD" } : a)))
        }
        onCancel={(listingId) =>
          setMyAuctions((prev) => prev.map((a) => (a.id === listingId ? { ...a, status: "CANCELLED" } : a)))
        }
        onCreateListing={(itemType, quantity, duration, startingPrice, buyNowPrice) =>
          setMyAuctions((prev) => [
            {
              id: `listing-${prev.length + 1}`,
              sellerId: "player-alice",
              sellerName: "Alice",
              itemType,
              quantity,
              createdAtMs: Date.now(),
              expiresAtMs: Date.now() + 24 * 3600_000,
              duration,
              startingPrice,
              buyNowPrice,
              currentBid: null,
              currentBidderId: null,
              currentBidderName: null,
              status: "ACTIVE",
              bidHistory: [],
            },
            ...prev,
          ])
        }
        onFilterChange={fn()}
      />
    </>
  );
}

export const Controlled: Story = {
  render: () => <ControlledStory />,
};

export const CreatingAuction: Story = {
  args: {
    ...Basic.args,
  },
  play: async () => {
    const body = within(document.body);
    // Radix keeps pointer-events off the dialog until its open animation ends;
    // skip the check so the story also renders headless (screenshots).
    await userEvent.click(body.getByText("Create Auction"), { pointerEventsCheck: 0 });
    await expect(body.getByText("Starting price")).toBeVisible();
    await expect(body.getByText("Buy now (optional)")).toBeVisible();
  },
};
