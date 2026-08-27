import { useMemo } from "react";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
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

function fmtDateTime(ms: number): string {
  return new Date(ms).toLocaleString();
}

interface Props {
  listing: AuctionListingData | null;
  itemMeta: Record<string, { label: string; bg: string }>;
  onClose: () => void;
}

export function AuctionDetail({ listing, itemMeta, onClose }: Props) {
  const history = useMemo(() => (listing ? [...listing.bidHistory].sort((a, b) => b.atMs - a.atMs) : []), [listing]);

  if (!listing) return null;
  const meta = itemMeta[listing.itemType] ?? { label: listing.itemType, bg: "#555" };

  return (
    <Dialog
      open={!!listing}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="w-[520px] max-w-[95vw]" movable>
        <DialogTitle>
          {meta.label} ×{listing.quantity}
        </DialogTitle>

        <div style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", marginTop: 8, marginBottom: 12 }}>
          <div>Seller: {listing.sellerName}</div>
          <div>Status: {listing.status}</div>
          <div>Starting price: {fmtCopper(listing.startingPrice)}</div>
          {listing.buyNowPrice !== null && <div>Buy now: {fmtCopper(listing.buyNowPrice)}</div>}
          {listing.currentBid !== null && (
            <div>
              Current bid: {fmtCopper(listing.currentBid)} ({listing.currentBidderName})
            </div>
          )}
          <div>Ends: {fmtDateTime(listing.expiresAtMs)}</div>
        </div>

        <div style={{ fontSize: 13, color: "#fff", marginBottom: 6 }}>Bid history</div>
        <div style={{ maxHeight: 320, overflowY: "auto", display: "flex", flexDirection: "column", gap: 6 }}>
          {history.length === 0 && (
            <div style={{ color: "rgba(255,255,255,0.4)", fontSize: 13, textAlign: "center", padding: 20 }}>
              No bids yet.
            </div>
          )}
          {history.map((bid, i) => (
            <div
              key={`${bid.atMs}-${i}`}
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: 12,
                padding: "6px 8px",
                border: "1px solid rgba(255,255,255,0.1)",
                borderRadius: 4,
              }}
            >
              <span>{bid.bidderName}</span>
              <span>{fmtCopper(bid.amount)}</span>
              <span style={{ color: "rgba(255,255,255,0.5)" }}>{fmtDateTime(bid.atMs)}</span>
            </div>
          ))}
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 12 }}>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
