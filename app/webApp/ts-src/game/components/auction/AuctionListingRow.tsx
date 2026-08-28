import { useState } from "react";
import { Button } from "../../../primitives/Button";
import { AuctionData } from "../../types";
import { CurrencyDisplay } from "./CurrencyDisplay";
import { CurrencyInput } from "./CurrencyInput";

function fmtTimeRemaining(expiresAtMs: number): string {
  const ms = expiresAtMs - Date.now();
  if (ms <= 0) return "Expired";
  const hours = Math.floor(ms / 3_600_000);
  const minutes = Math.floor((ms % 3_600_000) / 60_000);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

interface Props {
  listing: AuctionData;
  isMine: boolean;
  myPlayerId: string;
  itemMeta: Record<string, { label: string; bg: string }>;
  onBid: (listingId: string, amount: number) => void;
  onBuyNow: (listingId: string) => void;
  onCancel: (listingId: string) => void;
  onOpenDetail: (listingId: string) => void;
}

export function AuctionListingRow({
  listing,
  isMine,
  myPlayerId,
  itemMeta,
  onBid,
  onBuyNow,
  onCancel,
  onOpenDetail,
}: Props) {
  const [bidAmount, setBidAmount] = useState<number | null>(null);
  const [bidKey, setBidKey] = useState(0);
  const meta = itemMeta[listing.itemType] ?? { label: listing.itemType, bg: "#555" };
  const floor = listing.currentBid ?? listing.startingPrice;
  const active = listing.status === "ACTIVE";
  const isWinning = active && listing.currentBidderId === myPlayerId;

  const submitBid = () => {
    if (bidAmount === null || bidAmount <= floor) return;
    onBid(listing.id, bidAmount);
    setBidAmount(null);
    setBidKey((k) => k + 1);
  };

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: 10,
        border: isWinning ? "1px solid #4ade80" : "1px solid rgba(255,255,255,0.12)",
        borderRadius: 6,
      }}
    >
      <div
        style={{
          width: 44,
          height: 44,
          background: meta.bg,
          borderRadius: 4,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
          fontSize: 10,
          color: "#fff",
          textAlign: "center",
          padding: 2,
        }}
      >
        {meta.label}
      </div>

      <div style={{ flex: 1, minWidth: 0, cursor: "pointer" }} onClick={() => onOpenDetail(listing.id)}>
        <div style={{ fontSize: 13, color: "#fff" }}>
          {meta.label} ×{listing.quantity}
        </div>
        <div style={{ fontSize: 11, color: "rgba(255,255,255,0.5)" }}>
          Seller: {listing.sellerName} · {active ? fmtTimeRemaining(listing.expiresAtMs) : listing.status}
        </div>
        <div style={{ fontSize: 11, color: "rgba(255,255,255,0.5)", display: "flex", gap: 4, alignItems: "center" }}>
          {listing.currentBid !== null ? (
            <>
              Current bid: <CurrencyDisplay copper={listing.currentBid} /> ({listing.currentBidderName})
            </>
          ) : (
            <>
              Starting price: <CurrencyDisplay copper={listing.startingPrice} />
            </>
          )}
          {listing.buyNowPrice !== null && (
            <>
              · Buy now: <CurrencyDisplay copper={listing.buyNowPrice} />
            </>
          )}
          {isWinning && <span style={{ color: "#4ade80" }}> · You&apos;re winning</span>}
        </div>
      </div>

      {active && isMine && (
        <Button variant="danger" size="sm" onClick={() => onCancel(listing.id)}>
          Cancel
        </Button>
      )}

      {active && !isMine && (
        <>
          <CurrencyInput key={bidKey} onChange={setBidAmount} />
          <Button variant="secondary" size="sm" onClick={submitBid}>
            Bid
          </Button>
          {listing.buyNowPrice !== null && (
            <Button variant="primary" size="sm" onClick={() => onBuyNow(listing.id)}>
              Buy Now
            </Button>
          )}
        </>
      )}
    </div>
  );
}
