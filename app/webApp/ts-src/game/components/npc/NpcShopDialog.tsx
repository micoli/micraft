import { useState } from "react";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
import { ItemIcon } from "../../shared/ItemIcon";
import { NumberInput } from "../../../primitives/NumberInput";
import { NpcDialogData } from "../../types";

interface ItemMeta {
  label: string;
  bg: string;
}

interface Order {
  itemType: string;
  qty: number;
}

interface Props {
  data: NpcDialogData | null;
  wallet: number;
  itemMeta: Record<string, ItemMeta>;
  inventory: Record<string, number>;
  onClose: () => void;
  onBuy: (npcId: string, orders: Order[]) => void;
  onSell: (npcId: string, orders: Order[]) => void;
}

function formatCopper(copper: number): string {
  if (copper <= 0) return "0c";
  const g = Math.floor(copper / 100);
  const s = Math.floor((copper % 100) / 10);
  const c = copper % 10;
  const parts: string[] = [];
  if (g) parts.push(`${g}g`);
  if (s) parts.push(`${s}s`);
  if (c) parts.push(`${c}c`);
  return parts.join(" ");
}

const inputCls =
  "w-full bg-black/60 border border-white/20 text-white/80 font-mono text-[10px] rounded px-1.5 py-0.5 mb-1.5 focus:outline-none focus:border-white/50 placeholder-white/30";

const qtyCls =
  "w-12 bg-black/60 border border-white/20 text-white/80 font-mono text-[10px] rounded px-1 py-0.5 text-center focus:outline-none focus:border-white/50 flex-shrink-0";

export function NpcShopDialog({ data, wallet, itemMeta, inventory, onClose, onBuy, onSell }: Props) {
  const [shopFilter, setShopFilter] = useState("");
  const [invFilter, setInvFilter] = useState("");
  const [buyQtys, setBuyQtys] = useState<Record<string, number>>({});
  const [sellQtys, setSellQtys] = useState<Record<string, number>>({});

  if (!data || data.type !== "seller") return null;
  const npcId = data.npcId ?? "";
  const shopItems = data.shopItems ?? [];

  const sellPriceMap = new Map(shopItems.filter((i) => i.sellPrice > 0).map((i) => [i.itemType, i.sellPrice]));

  const sq = shopFilter.toLowerCase();
  const filteredShop = shopItems.filter((item) => {
    if (!sq) return true;
    const label = itemMeta[item.itemType]?.label ?? item.itemType;
    return label.toLowerCase().includes(sq) || item.itemType.toLowerCase().includes(sq);
  });

  const iq = invFilter.toLowerCase();
  const playerItems = Object.entries(inventory)
    .filter(([, qty]) => qty > 0)
    .filter(([type]) => {
      if (!iq) return true;
      const label = itemMeta[type]?.label ?? type;
      return label.toLowerCase().includes(iq) || type.toLowerCase().includes(iq);
    })
    .sort((a, b) => a[0].localeCompare(b[0]));

  const totalBuyCopper = shopItems.reduce((s, item) => s + (buyQtys[item.itemType] ?? 0) * item.buyPrice, 0);
  const totalSellCopper = playerItems.reduce((s, [type]) => {
    const price = sellPriceMap.get(type) ?? 0;
    return s + (sellQtys[type] ?? 0) * price;
  }, 0);

  const canBuy = totalBuyCopper > 0 && wallet >= totalBuyCopper;
  const canSell = totalSellCopper > 0;

  const handleBuy = () => {
    const orders = shopItems
      .map((item) => ({ itemType: item.itemType, qty: buyQtys[item.itemType] ?? 0 }))
      .filter((o) => o.qty > 0);
    if (orders.length) {
      onBuy(npcId, orders);
      setBuyQtys({});
    }
  };

  const handleSell = () => {
    const orders = playerItems
      .map(([type]) => ({ itemType: type, qty: sellQtys[type] ?? 0 }))
      .filter((o) => o.qty > 0 && (sellPriceMap.get(o.itemType) ?? 0) > 0);
    if (orders.length) {
      onSell(npcId, orders);
      setSellQtys({});
    }
  };

  const setBuyQty = (itemType: string, qty: number) =>
    setBuyQtys((prev) => ({ ...prev, [itemType]: Math.max(0, qty) }));

  const setSellQty = (itemType: string, qty: number, max: number) =>
    setSellQtys((prev) => ({ ...prev, [itemType]: Math.max(0, Math.min(max, qty)) }));

  return (
    <Dialog open={!!data} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="w-[680px] max-w-[95vw] font-mono shadow-[0_8px_32px_rgba(0,0,0,0.7)]">
        <div className="flex items-baseline justify-between mb-3">
          <DialogTitle className="text-base font-bold">{data.name}</DialogTitle>
          <span className="text-xs text-yellow-300">💰 {formatCopper(wallet)}</span>
        </div>

        <div className="flex gap-3">
          {/* Left — shop catalog */}
          <div className="flex-1 min-w-0 flex flex-col gap-1.5">
            <div className="text-[10px] text-white/45 uppercase tracking-wider">Boutique</div>

            <input
              type="text"
              value={shopFilter}
              onChange={(e) => setShopFilter(e.target.value)}
              placeholder="Filtrer…"
              className={inputCls}
              onKeyDown={(e) => e.stopPropagation()}
            />

            <div className="flex flex-col gap-1 max-h-[280px] overflow-y-auto pr-0.5">
              {filteredShop.map((item) => {
                const meta = itemMeta[item.itemType];
                const qty = buyQtys[item.itemType] ?? 0;
                return (
                  <div key={item.itemType} className="flex items-center gap-2 bg-white/5 rounded px-2 py-1">
                    <div className="w-7 h-7 flex items-center justify-center flex-shrink-0">
                      <ItemIcon itemId={item.itemType} fallbackBg={meta?.bg ?? "#555"} size={26} />
                    </div>
                    <span className="flex-1 text-xs text-white/80 truncate">{meta?.label ?? item.itemType}</span>
                    <span className="text-[9px] text-white/40 flex-shrink-0">{item.buyPrice}c</span>
                    <NumberInput
                      min={0}
                      value={qty || ""}
                      placeholder="0"
                      className={qtyCls}
                      onChange={(e) => setBuyQty(item.itemType, parseInt(e.target.value) || 0)}
                      onKeyDown={(e) => e.stopPropagation()}
                    />
                  </div>
                );
              })}
              {filteredShop.length === 0 && <p className="text-xs text-white/40 text-center py-4">Rien en stock.</p>}
            </div>

            {/* Summary + buy button */}
            <div className="flex items-center gap-2 bg-white/5 rounded px-2 py-1.5 mt-auto">
              <span className="flex-1 text-[10px] text-white/60">
                Total :{" "}
                <span className={totalBuyCopper > wallet ? "text-red-400" : "text-yellow-300"}>
                  {formatCopper(totalBuyCopper)}
                </span>
              </span>
              <Button
                variant="secondary"
                className="text-[9px] px-2 py-0.5 h-auto font-mono"
                onClick={handleBuy}
                disabled={!canBuy}
              >
                Acheter
              </Button>
            </div>
          </div>

          <div className="w-px bg-white/10 self-stretch" />

          {/* Right — player inventory */}
          <div className="flex-1 min-w-0 flex flex-col gap-1.5">
            <div className="text-[10px] text-white/45 uppercase tracking-wider">Mon inventaire</div>

            <input
              type="text"
              value={invFilter}
              onChange={(e) => setInvFilter(e.target.value)}
              placeholder="Filtrer…"
              className={inputCls}
              onKeyDown={(e) => e.stopPropagation()}
            />

            <div className="flex flex-col gap-1 max-h-[280px] overflow-y-auto pr-0.5">
              {playerItems.map(([type, owned]) => {
                const meta = itemMeta[type];
                const sellPrice = sellPriceMap.get(type);
                const qty = sellQtys[type] ?? 0;
                return (
                  <div key={type} className="flex items-center gap-2 bg-white/5 rounded px-2 py-1">
                    <div className="w-7 h-7 flex items-center justify-center flex-shrink-0">
                      <ItemIcon itemId={type} fallbackBg={meta?.bg ?? "#555"} size={26} />
                    </div>
                    <span className="flex-1 text-xs text-white/80 truncate">{meta?.label ?? type}</span>
                    <span className="text-[9px] text-white/40 flex-shrink-0">×{owned}</span>
                    {sellPrice !== undefined ? (
                      <>
                        <span className="text-[9px] text-white/40 flex-shrink-0">{sellPrice}c</span>
                        <NumberInput
                          min={0}
                          max={owned}
                          value={qty || ""}
                          placeholder="0"
                          className={qtyCls}
                          onChange={(e) => setSellQty(type, parseInt(e.target.value) || 0, owned)}
                          onKeyDown={(e) => e.stopPropagation()}
                        />
                      </>
                    ) : (
                      <span className="text-[9px] text-white/20 w-16 text-center flex-shrink-0">—</span>
                    )}
                  </div>
                );
              })}
              {playerItems.length === 0 && <p className="text-xs text-white/40 text-center py-4">Inventaire vide.</p>}
            </div>

            {/* Summary + sell button */}
            <div className="flex items-center gap-2 bg-white/5 rounded px-2 py-1.5 mt-auto">
              <span className="flex-1 text-[10px] text-white/60">
                Total : <span className="text-yellow-300">{formatCopper(totalSellCopper)}</span>
              </span>
              <Button
                variant="secondary"
                className="text-[9px] px-2 py-0.5 h-auto font-mono"
                onClick={handleSell}
                disabled={!canSell}
              >
                Vendre
              </Button>
            </div>
          </div>
        </div>

        <Button variant="secondary" onClick={onClose} className="font-mono mt-3 w-full">
          Fermer
        </Button>
      </DialogContent>
    </Dialog>
  );
}
