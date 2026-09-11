import { useState } from "react";
import { AuctionHouse } from "../../game/components/auction/AuctionHouse";
import type { AuctionData, AuctionFilter } from "../../game/types";
import { useHubMessage, useHubSend, useHubSocket } from "../state/HubSocketProvider";
import type { AuctionListingsUpdateMsg } from "../lib/hubCodec";

/**
 * Reuses the exact in-game `AuctionHouse` (filters, listing rows, bid/buy/cancel, create form) —
 * every action is already a plain callback prop there, so wiring it to the hub socket instead of
 * the wasm bridge needed zero changes to that component. `chrome="page"` fills `<main>` instead of
 * floating over it. `open` stays true, `onClose` a no-op.
 */
export function AuctionRoute() {
  const [listings, setListings] = useState<AuctionData[]>([]);
  const { playerId, inventory, itemMeta } = useHubSocket();
  const send = useHubSend();

  useHubMessage<AuctionListingsUpdateMsg>("AuctionListingsUpdate", (p) => setListings(p.listings));

  return (
    <AuctionHouse
      open
      auctions={listings}
      myPlayerId={playerId}
      inventory={inventory}
      itemMeta={itemMeta}
      onClose={() => {}}
      chrome="page"
      onBid={(listingId, amount) => send("AuctionPlaceBid", { listingId, amount })}
      onBuyNow={(listingId) => send("AuctionBuyNow", { listingId })}
      onCancel={(listingId) => send("AuctionCancelListing", { listingId })}
      onCreateListing={(itemType, quantity, duration, startingPrice, buyNowPrice) =>
        send("AuctionCreateListing", { itemType, quantity, duration, startingPrice, buyNowPrice })
      }
      onFilterChange={(filter: AuctionFilter) => send("AuctionSetFilter", { filter })}
    />
  );
}
