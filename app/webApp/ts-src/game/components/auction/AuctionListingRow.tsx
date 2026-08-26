import { useState } from "react";
import { Button } from "../../../primitives/Button";
import { Input } from "../../../primitives/Input";
import { AuctionListingData } from "../../types";

function fmtCopper(copper: number): string {
  const g = Math.floor(copper / 100);
  const s = Math.floor((copper % 100) / 10);
  const c = copper % 10;
  const parts: string[] = [];
  if (g > 0) parts.push(`${g}g`);
  if (s > 0) parts.push(`${s}s`);
  if (c > 0 || parts.length === 0) parts.push(`${c}c`);
  return parts.join(" ");
}

function fmtTimeRemaining(expiresAtMs: number): string {
  const ms = expiresAtMs - Date.now();
  if (ms <= 0) return "Expired";
  const hours = Math.floor(ms / 3_600_000);
  const minutes = Math.floor((ms % 3_600_000) / 60_000);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

interface Props {
  listing: AuctionListingData;
  isMine: boolean;
  itemMeta: Record<string, { label: string; bg: string }>;
  onBid: (listingId: string, amount: number) => void;
  onBuyNow: (listingId: string) => void;
  onCancel: (listingId: string) => void;
}

export function AuctionListingRow({ listing, isMine, itemMeta, onBid, onBuyNow, onCancel }: Props) {
  const [bidAmount, setBidAmount] = useState("");
  const meta = itemMeta[listing.itemType] ?? { label: listing.itemType, bg: "#555" };
  const floor = listing.currentBid ?? listing.startingPrice;
  const active = listing.status === "ACTIVE";

  const submitBid = () => {
    const amount = Number(bidAmount);
    if (!Number.isFinite(amount) || amount <= floor) return;
    onBid(listing.id, amount);
    setBidAmount("");
  };

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: 10,
        border: "1px solid rgba(255,255,255,0.12)",
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

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, color: "#fff" }}>
          {meta.label} ×{listing.quantity}
        </div>
        <div style={{ fontSize: 11, color: "rgba(255,255,255,0.5)" }}>
          Seller: {listing.sellerName} · {active ? fmtTimeRemaining(listing.expiresAtMs) : listing.status}
        </div>
        <div style={{ fontSize: 11, color: "rgba(255,255,255,0.5)" }}>
          {listing.currentBid !== null
            ? `Current bid: ${fmtCopper(listing.currentBid)} (${listing.currentBidderName})`
            : `Starting price: ${fmtCopper(listing.startingPrice)}`}
          {listing.buyNowPrice !== null && ` · Buy now: ${fmtCopper(listing.buyNowPrice)}`}
        </div>
      </div>

      {active && isMine && (
        <Button variant="danger" size="sm" onClick={() => onCancel(listing.id)}>
          Cancel
        </Button>
      )}

      {active && !isMine && (
        <>
          <Input
            type="number"
            placeholder={`> ${floor}`}
            value={bidAmount}
            onChange={(e) => setBidAmount(e.target.value)}
            style={{ width: 90 }}
          />
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
