import { useState } from "react";
import { type BlockInfoDto } from "../../../apiTypes";
import { type useAdminShortcutBar } from "./useAdminShortcutBar";
import { CssBlockCube } from "../../../../game/shared/BlockPreview";
import { BlockHoverTooltip } from "./BlockHoverTooltip";

export function VoxelShortcutBarSlot({
  shortcutBar,
  idx,
  slotBlock,
  getOrdinal,
  blockDefs,
  getPreview,
  blockDefsReady,
  previewsReady,
  hovered,
  onHoverEnter,
  onHoverLeave,
}: {
  shortcutBar: ReturnType<typeof useAdminShortcutBar>;
  idx: number;
  slotBlock: string | null;
  getOrdinal: (name: string) => number | null;
  blockDefs: BlockInfoDto[];
  getPreview: (ordinal: number) => string | null;
  blockDefsReady: boolean;
  previewsReady: boolean;
  hovered: boolean;
  onHoverEnter: () => void;
  onHoverLeave: () => void;
}) {
  const isBreakSlot = idx === 0;
  const isSelected = shortcutBar.selectedSlot === idx;
  const isDropTarget = shortcutBar.dragOver === idx;
  const ordinal = !isBreakSlot && slotBlock ? getOrdinal(slotBlock) : null;
  const slotBlockInfo = slotBlock ? blockDefs.find((b) => b.name === slotBlock) : null;
  const [anchorRect, setAnchorRect] = useState<DOMRect | null>(null);

  return (
    <div
      onClick={() => shortcutBar.selectSlot(idx)}
      onDragOver={(e) => shortcutBar.handleDragOver(e, idx)}
      onDragLeave={shortcutBar.handleDragLeave}
      onDrop={(e) => shortcutBar.handleDrop(e, idx)}
      onContextMenu={(e) => shortcutBar.handleContextMenu(e, idx)}
      onMouseEnter={(e) => {
        setAnchorRect(e.currentTarget.getBoundingClientRect());
        onHoverEnter();
      }}
      onMouseLeave={onHoverLeave}
      title={isBreakSlot ? "Break" : (slotBlock ?? undefined)}
      className={`relative flex flex-col items-center gap-0.5 rounded border-2 p-1 w-14 cursor-pointer transition-colors ${
        isDropTarget ? "bg-white/20" : "bg-black/30"
      } ${isSelected ? "border-[#3C50E0]" : "border-transparent hover:border-white/20"}`}
    >
      <div className="absolute top-0 left-0.5 text-[#8A99AF] font-mono text-[7px]">
        {idx === 9 ? "0" : String(idx + 1)}
      </div>
      <div className="h-4 w-4 flex items-center justify-center shrink-0">
        {isBreakSlot ? (
          <div className="text-sm leading-none">⛏</div>
        ) : slotBlock && ordinal !== null && previewsReady ? (
          getPreview(ordinal) ? (
            <img
              alt=""
              src={getPreview(ordinal)!}
              width={16}
              height={16}
              style={{ imageRendering: "pixelated", display: "block" }}
            />
          ) : blockDefsReady ? (
            <CssBlockCube ordinal={ordinal} size={16} />
          ) : null
        ) : null}
      </div>
      <span className="text-[9px] leading-tight text-[#8A99AF] text-center truncate w-full">
        {slotBlockInfo?.name?.replace(/_/g, " ") ?? "-"}
      </span>
      {hovered && slotBlockInfo && anchorRect && (
        <BlockHoverTooltip
          block={slotBlockInfo}
          ordinal={ordinal}
          anchorRect={anchorRect}
          previewsReady={previewsReady}
        />
      )}
    </div>
  );
}
