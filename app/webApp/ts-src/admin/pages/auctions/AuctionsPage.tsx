import { useEffect, useState } from "react";
import { getApiAdminAuctions, postApiAdminAuctionsByIdForceCancel } from "../../../generated/api/requests";
import { AuctionListingDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { Button } from "../../../primitives/Button";
import { Badge } from "../../../primitives/Badge";

function fmtCopper(copper: number): string {
  const g = Math.floor(copper / 100);
  const s = Math.floor((copper % 100) / 10);
  const c = copper % 10;
  return `${g}g ${s}s ${c}c`;
}

function statusColor(status: string) {
  switch (status) {
    case "SOLD":
      return "bg-green-900/60 text-green-300";
    case "CANCELLED":
      return "bg-[#2E3A4E] text-[#8A99AF]";
    case "EXPIRED":
      return "bg-yellow-900/60 text-yellow-300";
    default:
      return "bg-blue-900/60 text-blue-300";
  }
}

export function AuctionsPage() {
  const t = useT();
  const [listings, setListings] = useState<AuctionListingDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  const load = () => {
    getApiAdminAuctions({ throwOnError: true })
      .then((r) => setListings(r.data))
      .catch((e) => setError(String(e)));
  };

  useEffect(() => {
    load();
  }, []);

  const forceCancel = (id: string) => {
    setPendingId(id);
    postApiAdminAuctionsByIdForceCancel({ path: { id }, throwOnError: true })
      .then(load)
      .catch((e) => setError(String(e)))
      .finally(() => setPendingId(null));
  };

  if (error) return <div className="p-4 text-red-400">{error}</div>;
  if (!listings) return <div className="p-4 text-[#8A99AF]">{t("common.loading")}</div>;

  return (
    <div className="p-4">
      <h1 className="text-xl font-semibold text-white mb-4">{t("page.auctions")}</h1>
      <table className="w-full text-sm text-left text-[#8A99AF]">
        <thead>
          <tr className="border-b border-[#2E3A4E]">
            <th className="py-2 pr-4">Seller</th>
            <th className="py-2 pr-4">Item</th>
            <th className="py-2 pr-4">Qty</th>
            <th className="py-2 pr-4">Status</th>
            <th className="py-2 pr-4">Current Bid</th>
            <th className="py-2 pr-4">Buy Now</th>
            <th className="py-2 pr-4">Expires</th>
            <th className="py-2 pr-4"></th>
          </tr>
        </thead>
        <tbody>
          {listings.map((l) => (
            <tr key={l.id} className="border-b border-[#1C2434]">
              <td className="py-2 pr-4">{l.sellerName}</td>
              <td className="py-2 pr-4">{l.itemType}</td>
              <td className="py-2 pr-4">{l.quantity}</td>
              <td className="py-2 pr-4">
                <Badge color={statusColor(l.status)}>{l.status}</Badge>
              </td>
              <td className="py-2 pr-4">
                {l.currentBid != null ? `${fmtCopper(l.currentBid)} (${l.currentBidderName})` : "—"}
              </td>
              <td className="py-2 pr-4">{l.buyNowPrice != null ? fmtCopper(l.buyNowPrice) : "—"}</td>
              <td className="py-2 pr-4">{new Date(l.expiresAtMs).toLocaleString()}</td>
              <td className="py-2 pr-4">
                {l.status === "ACTIVE" && (
                  <Button variant="danger" size="sm" disabled={pendingId === l.id} onClick={() => forceCancel(l.id)}>
                    Force Cancel
                  </Button>
                )}
              </td>
            </tr>
          ))}
          {listings.length === 0 && (
            <tr>
              <td colSpan={8} className="py-6 text-center">
                No auction listings.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
