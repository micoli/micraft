import { useMemo, useState } from "react";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
import { Input } from "../../../primitives/Input";
import { AuctionListingData } from "../../types";
import { AuctionListingRow } from "./AuctionListingRow";
import { CreateListingForm } from "./CreateListingForm";

interface Props {
  open: boolean;
  listings: AuctionListingData[];
  myPlayerId: string;
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  onClose: () => void;
  onBid: (listingId: string, amount: number) => void;
  onBuyNow: (listingId: string) => void;
  onCancel: (listingId: string) => void;
  onCreateListing: (
    itemType: string,
    quantity: number,
    duration: "H12" | "H24" | "H48" | "H96",
    startingPrice: number,
    buyNowPrice: number | null,
  ) => void;
}

export function AuctionHouse({
  open,
  listings,
  myPlayerId,
  inventory,
  itemMeta,
  onClose,
  onBid,
  onBuyNow,
  onCancel,
  onCreateListing,
}: Props) {
  const [creating, setCreating] = useState(false);
  const [itemFilter, setItemFilter] = useState("");
  const [sellerFilter, setSellerFilter] = useState("");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [mineOnly, setMineOnly] = useState(false);
  const [expiredOnly, setExpiredOnly] = useState(false);

  const filtered = useMemo(() => {
    const min = minPrice.trim() === "" ? null : Number(minPrice);
    const max = maxPrice.trim() === "" ? null : Number(maxPrice);
    return listings.filter((l) => {
      if (mineOnly && l.sellerId !== myPlayerId) return false;
      if (expiredOnly && l.status === "ACTIVE") return false;
      if (!expiredOnly && !mineOnly && l.status !== "ACTIVE") return false;
      if (itemFilter && !l.itemType.toLowerCase().includes(itemFilter.toLowerCase())) return false;
      if (sellerFilter && !l.sellerName.toLowerCase().includes(sellerFilter.toLowerCase())) return false;
      const price = l.currentBid ?? l.startingPrice;
      if (min !== null && price < min) return false;
      if (max !== null && price > max) return false;
      return true;
    });
  }, [listings, itemFilter, sellerFilter, minPrice, maxPrice, mineOnly, expiredOnly, myPlayerId]);

  const handleCreate = (
    itemType: string,
    quantity: number,
    duration: "H12" | "H24" | "H48" | "H96",
    startingPrice: number,
    buyNowPrice: number | null,
  ) => {
    onCreateListing(itemType, quantity, duration, startingPrice, buyNowPrice);
    setCreating(false);
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="w-[720px] max-w-[95vw]" movable>
        <DialogTitle>Auction House</DialogTitle>

        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12, marginBottom: 12 }}>
          <Input
            placeholder="Filter by item"
            value={itemFilter}
            onChange={(e) => setItemFilter(e.target.value)}
            style={{ width: 140 }}
          />
          <Input
            placeholder="Filter by seller"
            value={sellerFilter}
            onChange={(e) => setSellerFilter(e.target.value)}
            style={{ width: 140 }}
          />
          <Input
            type="number"
            placeholder="Min price"
            value={minPrice}
            onChange={(e) => setMinPrice(e.target.value)}
            style={{ width: 100 }}
          />
          <Input
            type="number"
            placeholder="Max price"
            value={maxPrice}
            onChange={(e) => setMaxPrice(e.target.value)}
            style={{ width: 100 }}
          />
          <Button
            variant={mineOnly ? "primary" : "outline"}
            size="sm"
            onClick={() => {
              setMineOnly((v) => !v);
              setExpiredOnly(false);
            }}
          >
            My Auctions
          </Button>
          <Button
            variant={expiredOnly ? "primary" : "outline"}
            size="sm"
            onClick={() => {
              setExpiredOnly((v) => !v);
              setMineOnly(false);
            }}
          >
            Expired
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setCreating((v) => !v)}>
            {creating ? "Close Form" : "Create Listing"}
          </Button>
        </div>

        {creating && (
          <CreateListingForm
            inventory={inventory}
            itemMeta={itemMeta}
            onSubmit={handleCreate}
            onCancel={() => setCreating(false)}
          />
        )}

        <div style={{ maxHeight: 420, overflowY: "auto", display: "flex", flexDirection: "column", gap: 8 }}>
          {filtered.length === 0 && (
            <div style={{ color: "rgba(255,255,255,0.4)", fontSize: 13, textAlign: "center", padding: 20 }}>
              No listings match these filters.
            </div>
          )}
          {filtered.map((listing) => (
            <AuctionListingRow
              key={listing.id}
              listing={listing}
              isMine={listing.sellerId === myPlayerId}
              itemMeta={itemMeta}
              onBid={onBid}
              onBuyNow={onBuyNow}
              onCancel={onCancel}
            />
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
