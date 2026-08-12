import { type BlockInfoDto } from "../../api";
import { Block3DPreview } from "../../../game/shared/Block3DPreview";

export function BlockHoverTooltip({
  block,
  ordinal,
  anchorRect,
  previewsReady,
}: {
  block: BlockInfoDto;
  ordinal: number | null;
  anchorRect: DOMRect;
  previewsReady: boolean;
}) {
  // Fixed positioning (viewport-relative) instead of absolute-inside-the-scroll-container: the
  // palette list clips overflow (overflow-y-auto) for scrolling, so an absolutely positioned
  // tooltip anchored to a button near the top/bottom/only row of the (possibly filtered) list
  // gets clipped by that container regardless of which side it's placed on. Flip above/below
  // based on real viewport space so the tooltip is always fully visible.
  const above = anchorRect.bottom + 90 > window.innerHeight;
  const top = above ? anchorRect.top - 4 : anchorRect.bottom + 4;
  const left = anchorRect.left + anchorRect.width / 2;
  return (
    <div
      className="pointer-events-none fixed z-30 flex flex-col items-center gap-0.5 whitespace-nowrap rounded border border-[#2E3A4E] bg-[#0B1220] px-2 py-1 text-[10px] shadow-lg"
      style={{ left, top, transform: above ? "translate(-50%, -100%)" : "translate(-50%, 0)" }}
    >
      {ordinal !== null && previewsReady && <Block3DPreview ordinal={ordinal} size={72} />}
      <span className="font-semibold text-white">{block.name.replace(/_/g, " ")}</span>
      <span className="text-[#8A99AF]">
        {block.hardness < 0 ? "Unbreakable" : `Hardness ${block.hardness}`} · {block.solid ? "Solid" : "Non-solid"}
        {block.transparent ? " · Transparent" : ""}
        {block.liquid ? " · Liquid" : ""}
      </span>
    </div>
  );
}
