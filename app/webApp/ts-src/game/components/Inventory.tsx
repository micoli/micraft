import { useEffect, useState } from "react";
import { cn } from "../../primitives/cn";
import { getItemVisual } from "../lib/blockDefs";
import { ItemIcon } from "../shared/ItemIcon";
import { ItemTooltip } from "../shared/ItemTooltip";
import { useInventory } from "../hooks/useInventory";
import { ItemMetaEntry, PreferencesData } from "../types";

type SortKey = "count" | "type" | "label" | "extendedName" | "color";

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: "type", label: "Type" },
  { value: "label", label: "Label" },
  { value: "extendedName", label: "Nom étendu" },
  { value: "color", label: "Couleur" },
  { value: "count", label: "Nombre" },
];

interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, ItemMetaEntry>;
  visible: boolean;
  layoutStyle?: React.CSSProperties;
  preferences?: PreferencesData | null;
  onSortChange?: (sortA: SortKey, sortB: SortKey) => void;
  wallet?: number;
}

function getItemKind(type: string, meta: ItemMetaEntry): string {
  const { ordinal } = getItemVisual(type);
  if (ordinal != null) return "block";
  if (meta.consumable) return "consumable";
  return "item";
}

function sortItems(
  items: { type: string; count: number; meta: ItemMetaEntry }[],
  sortA: SortKey,
  sortB: SortKey,
): { type: string; count: number; meta: ItemMetaEntry }[] {
  const key = (item: { type: string; count: number; meta: ItemMetaEntry }, s: SortKey): string | number => {
    switch (s) {
      case "count":
        return -item.count;
      case "type":
        return getItemKind(item.type, item.meta);
      case "label":
        return item.meta.label.toLowerCase();
      case "extendedName":
        return item.type.toLowerCase();
      case "color":
        return item.meta.bg;
    }
  };
  return [...items].sort((a, b) => {
    const ka = key(a, sortA);
    const kb = key(b, sortA);
    if (ka < kb) return -1;
    if (ka > kb) return 1;
    const ka2 = key(a, sortB);
    const kb2 = key(b, sortB);
    if (ka2 < kb2) return -1;
    if (ka2 > kb2) return 1;
    return 0;
  });
}

export function Inventory({ inventory, itemMeta, visible, layoutStyle, preferences, onSortChange, wallet }: Props) {
  const { startDrag, moveDrag, endDrag } = useInventory();
  const [hoveredType, setHoveredType] = useState<string | null>(null);
  const [filter, setFilter] = useState("");

  const [localSortA, setLocalSortA] = useState<SortKey>(() => (preferences?.inventorySortA as SortKey) ?? "type");
  const [localSortB, setLocalSortB] = useState<SortKey>(() => (preferences?.inventorySortB as SortKey) ?? "label");

  useEffect(() => {
    if (preferences?.inventorySortA) setLocalSortA(preferences.inventorySortA as SortKey);
  }, [preferences?.inventorySortA]);

  useEffect(() => {
    if (preferences?.inventorySortB) setLocalSortB(preferences.inventorySortB as SortKey);
  }, [preferences?.inventorySortB]);

  if (!visible) return null;

  const q = filter.toLowerCase();
  const rawItems = Object.keys(inventory)
    .filter((type) => (inventory[type] ?? 0) > 0 && itemMeta[type] !== undefined)
    .map((type) => ({ type, count: inventory[type], meta: itemMeta[type] }))
    .filter(({ type, meta }) => !q || meta.label.toLowerCase().includes(q) || type.toLowerCase().includes(q));

  const items = sortItems(rawItems, localSortA, localSortB);

  const handleSortA = (v: SortKey) => {
    setLocalSortA(v);
    onSortChange?.(v, localSortB);
  };
  const handleSortB = (v: SortKey) => {
    setLocalSortB(v);
    onSortChange?.(localSortA, v);
  };

  const selectCls =
    "bg-black/80 border border-white/25 text-white/80 font-mono text-[10px] rounded px-1 py-0.5 cursor-pointer focus:outline-none focus:border-white/50 flex-1";

  return (
    <div
      className={cn(
        "pointer-events-auto z-[998] bg-black/75 border border-white/30 rounded-md py-2 px-3 min-w-[120px]",
        !layoutStyle && "fixed bottom-24 left-1/2 -translate-x-1/2",
      )}
      style={layoutStyle}
    >
      {/* Wallet */}
      {wallet !== undefined && wallet > 0 && (
        <div className="text-[10px] font-mono text-yellow-300 mb-1 text-right">
          💰 {Math.floor(wallet / 100)}g {Math.floor((wallet % 100) / 10)}s {wallet % 10}c
        </div>
      )}

      {/* Filter */}
      <input
        type="text"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        placeholder="Filtrer…"
        className="w-full bg-black/60 border border-white/20 text-white/80 font-mono text-[10px] rounded px-1.5 py-0.5 mb-1.5 focus:outline-none focus:border-white/50 placeholder-white/30"
        onKeyDown={(e) => e.stopPropagation()}
      />

      {/* Sort combos */}
      <div className="flex gap-1 mb-2">
        <select className={selectCls} value={localSortA} onChange={(e) => handleSortA(e.target.value as SortKey)}>
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select className={selectCls} value={localSortB} onChange={(e) => handleSortB(e.target.value as SortKey)}>
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>

      {/* Grid */}
      {items.length === 0 ? (
        <div className="text-white/35 font-mono text-xs text-center px-4 py-2">Inventory empty</div>
      ) : (
        <div className="flex flex-wrap">
          {items.map(({ type, count, meta }) => (
            <div
              key={type}
              onPointerDown={(e) => startDrag(e, type, meta.bg)}
              onPointerMove={moveDrag}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
              onPointerEnter={() => setHoveredType(type)}
              onPointerLeave={() => setHoveredType(null)}
              className="w-[52px] h-[52px] bg-black/72 border border-white/30 flex flex-col items-center justify-center relative cursor-grab touch-none"
            >
              <ItemIcon itemId={type} fallbackBg={meta.bg} />
              <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                {meta.label}
              </div>
              <div className="absolute bottom-0.5 right-1 text-white font-mono font-bold text-[10px] [text-shadow:1px_1px_0_#000]">
                {count}
              </div>
              {hoveredType === type && <ItemTooltip type={type} count={count} meta={meta} />}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
