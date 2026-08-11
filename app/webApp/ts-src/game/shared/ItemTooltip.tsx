import { getItemVisual } from "../blocks/blockDefs";
import { ItemMetaEntry } from "../types";
import { Block3DPreview } from "./Block3DPreview";

export function ItemTooltip({ type, count, meta }: { type: string; count: number; meta: ItemMetaEntry }) {
  const { ordinal, colorHex } = getItemVisual(type);
  const kind = ordinal != null ? "Block" : meta.consumable ? "Consommable" : "Item";
  return (
    <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 z-[9999] bg-black/90 border border-white/30 rounded-md px-3 py-2 text-white font-mono text-xs whitespace-nowrap pointer-events-none flex flex-col items-center gap-0.5 min-w-[130px]">
      {ordinal != null && <Block3DPreview ordinal={ordinal} size={72} colorHex={colorHex} />}
      <div className="font-bold text-[11px] text-white">{meta.label}</div>
      <div className="text-white/50 text-[9px] break-all">{type}</div>
      <div className="text-white/60 text-[10px] mt-0.5">
        <span className="text-white/40">Type</span> {kind}
      </div>
      <div className="text-white/60 text-[10px]">
        <span className="text-white/40">Qté</span> {count}
      </div>
      {(meta.healthRestore ?? 0) > 0 && <div className="text-green-400 text-[10px]">❤ +{meta.healthRestore}</div>}
      {(meta.manaRestore ?? 0) > 0 && <div className="text-blue-400 text-[10px]">✦ +{meta.manaRestore}</div>}
      {meta.plainColor && (
        <div className="text-white/60 text-[10px]">
          <span className="text-white/40">Couleur</span> {meta.plainColor}
        </div>
      )}
    </div>
  );
}
