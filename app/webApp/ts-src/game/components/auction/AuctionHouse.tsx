import { useEffect, useState } from "react";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
import { Input } from "../../../primitives/Input";
import { AuctionFilter, AuctionData } from "../../types";
import { AuctionListingRow } from "./AuctionListingRow";
import { AuctionDetail } from "./AuctionDetail";
import { CreateAuctionForm } from "./CreateAuctionForm";
import { CurrencyInput } from "./CurrencyInput";

const FILTER_DEBOUNCE_MS = 300;

interface Props {
  open: boolean;
  auctions: AuctionData[];
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
  onFilterChange: (filter: AuctionFilter) => void;
  /** Radix modal behavior (focus trap + `aria-hidden` outside the dialog) — see
   *  MailboxOverlay.tsx's `modal` prop doc for why the hub passes `false`. */
  modal?: boolean;
  /** "dialog" (default): the game's floating window. "page": no Dialog/Portal — fills whatever
   *  block-level container it's rendered in, for the hub where each screen owns the whole main
   *  content area. See MailboxOverlay.tsx's `chrome` prop doc. */
  chrome?: "dialog" | "page";
}

export function AuctionHouse({
  open,
  auctions,
  myPlayerId,
  inventory,
  itemMeta,
  onClose,
  onBid,
  onBuyNow,
  onCancel,
  onCreateListing,
  onFilterChange,
  modal = true,
  chrome = "dialog",
}: Props) {
  const [creating, setCreating] = useState(false);
  const [itemFilter, setItemFilter] = useState("");
  const [sellerFilter, setSellerFilter] = useState("");
  const [minPrice, setMinPrice] = useState<number | null>(null);
  const [maxPrice, setMaxPrice] = useState<number | null>(null);
  const [mineOnly, setMineOnly] = useState(false);
  const [expiredOnly, setExpiredOnly] = useState(false);
  const [myBidsOnly, setMyBidsOnly] = useState(false);
  const [selectedListingId, setSelectedListingId] = useState<string | null>(null);

  useEffect(() => {
    const handle = setTimeout(() => {
      onFilterChange({
        itemType: itemFilter.trim() || null,
        sellerName: sellerFilter.trim() || null,
        minPrice,
        maxPrice,
        mineOnly,
        expiredOnly,
        myBidsOnly,
      });
    }, FILTER_DEBOUNCE_MS);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [itemFilter, sellerFilter, minPrice, maxPrice, mineOnly, expiredOnly, myBidsOnly]);

  const selectedListing = selectedListingId ? (auctions.find((l) => l.id === selectedListingId) ?? null) : null;

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

  const body = (
    <>
      {chrome === "page" ? (
        <div className="text-lg font-semibold text-white/90">Auction House</div>
      ) : (
        <DialogTitle>Auction House</DialogTitle>
      )}

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
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12, marginBottom: 12 }}>
        <CurrencyInput onChange={setMinPrice} />
        <CurrencyInput onChange={setMaxPrice} />
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12, marginBottom: 12 }}>
        <Button
          variant={mineOnly ? "primary" : "outline"}
          size="sm"
          onClick={() => {
            setMineOnly((v) => !v);
            setExpiredOnly(false);
            setMyBidsOnly(false);
          }}
        >
          My Auctions
        </Button>
        <Button
          variant={myBidsOnly ? "primary" : "outline"}
          size="sm"
          onClick={() => {
            setMyBidsOnly((v) => !v);
            setExpiredOnly(false);
            setMineOnly(false);
          }}
        >
          My Bids
        </Button>
        <Button
          variant={expiredOnly ? "primary" : "outline"}
          size="sm"
          onClick={() => {
            setExpiredOnly((v) => !v);
            setMineOnly(false);
            setMyBidsOnly(false);
          }}
        >
          Expired
        </Button>
        <Button variant="secondary" size="sm" onClick={() => setCreating((v) => !v)}>
          {creating ? "Close Form" : "Create Auction"}
        </Button>
      </div>

      {creating && (
        <CreateAuctionForm
          inventory={inventory}
          itemMeta={itemMeta}
          onSubmit={handleCreate}
          onCancel={() => setCreating(false)}
        />
      )}

      <div style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "flex", flexDirection: "column", gap: 8 }}>
        {auctions.length === 0 && (
          <div style={{ color: "rgba(255,255,255,0.4)", fontSize: 13, textAlign: "center", padding: 20 }}>
            No listings match these filters.
          </div>
        )}
        {auctions.map((listing) => (
          <AuctionListingRow
            key={listing.id}
            listing={listing}
            isMine={listing.sellerId === myPlayerId}
            myPlayerId={myPlayerId}
            itemMeta={itemMeta}
            onBid={onBid}
            onBuyNow={onBuyNow}
            onCancel={onCancel}
            onOpenDetail={setSelectedListingId}
          />
        ))}
      </div>

      <AuctionDetail listing={selectedListing} itemMeta={itemMeta} onClose={() => setSelectedListingId(null)} />
    </>
  );

  if (chrome === "page") {
    if (!open) return null;
    return <div className="flex flex-col h-full p-4">{body}</div>;
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
      modal={modal}
    >
      <DialogContent className="flex flex-col" windowMode="maximized">
        {body}
      </DialogContent>
    </Dialog>
  );
}
