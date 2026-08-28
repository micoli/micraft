import { useState } from "react";
import { Button } from "../../../primitives/Button";
import { NumberInput } from "../../../primitives/NumberInput";
import { CurrencyInput } from "./CurrencyInput";

const DURATIONS: { value: "H12" | "H24" | "H48" | "H96"; label: string; taxPercent: number }[] = [
  { value: "H12", label: "12h", taxPercent: 3 },
  { value: "H24", label: "24h", taxPercent: 6 },
  { value: "H48", label: "48h", taxPercent: 10 },
  { value: "H96", label: "96h", taxPercent: 15 },
];

interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  onSubmit: (
    itemType: string,
    quantity: number,
    duration: "H12" | "H24" | "H48" | "H96",
    startingPrice: number,
    buyNowPrice: number | null,
  ) => void;
  onCancel: () => void;
}

export function CreateAuctionForm({ inventory, itemMeta, onSubmit, onCancel }: Props) {
  const ownedItems = Object.entries(inventory).filter(([, count]) => count > 0);
  const [itemType, setItemType] = useState(ownedItems[0]?.[0] ?? "");
  const [quantity, setQuantity] = useState("1");
  const [duration, setDuration] = useState<"H12" | "H24" | "H48" | "H96">("H24");
  const [startingPrice, setStartingPrice] = useState<number | null>(null);
  const [buyNowPrice, setBuyNowPrice] = useState<number | null>(null);

  const maxQuantity = inventory[itemType] ?? 0;
  const qty = Number(quantity);
  const start = startingPrice ?? 0;
  const valid =
    itemType !== "" && qty > 0 && qty <= maxQuantity && start > 0 && (buyNowPrice === null || buyNowPrice > start);

  return (
    <div
      style={{
        border: "1px solid rgba(255,255,255,0.15)",
        borderRadius: 6,
        padding: 12,
        marginBottom: 12,
        display: "flex",
        flexDirection: "column",
        gap: 8,
      }}
    >
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <label style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", width: 90 }}>Item</label>
        <select
          value={itemType}
          onChange={(e) => setItemType(e.target.value)}
          style={{ flex: 1, background: "#111", color: "#eee", border: "1px solid #555", borderRadius: 4, padding: 6 }}
        >
          {ownedItems.map(([type, count]) => (
            <option key={type} value={type}>
              {itemMeta[type]?.label ?? type} (×{count})
            </option>
          ))}
        </select>
      </div>

      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <label style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", width: 90 }}>Quantity</label>
        <NumberInput value={quantity} onChange={(e) => setQuantity(e.target.value)} min={1} max={maxQuantity} />
      </div>

      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <label style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", width: 90 }}>Duration</label>
        <div style={{ display: "flex", gap: 6 }}>
          {DURATIONS.map((d) => (
            <button
              key={d.value}
              type="button"
              onClick={() => setDuration(d.value)}
              style={{
                padding: "4px 8px",
                borderRadius: 4,
                fontSize: 11,
                border: duration === d.value ? "1px solid #4ade80" : "1px solid rgba(255,255,255,0.2)",
                background: duration === d.value ? "rgba(74,222,128,0.15)" : "transparent",
                color: "#fff",
                cursor: "pointer",
              }}
            >
              {d.label} ({d.taxPercent}% tax)
            </button>
          ))}
        </div>
      </div>

      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <label style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", width: 90 }}>Starting price</label>
        <CurrencyInput onChange={setStartingPrice} />
      </div>

      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <label style={{ fontSize: 12, color: "rgba(255,255,255,0.6)", width: 90 }}>Buy now (optional)</label>
        <CurrencyInput onChange={setBuyNowPrice} />
      </div>

      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 4 }}>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button
          variant="primary"
          disabled={!valid}
          onClick={() => onSubmit(itemType, qty, duration, start, buyNowPrice)}
        >
          Create Auction
        </Button>
      </div>
    </div>
  );
}
