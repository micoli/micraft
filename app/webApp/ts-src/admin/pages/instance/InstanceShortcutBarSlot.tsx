import { type BlockInfoDto } from "../../api";
import { type useInstanceShortcutBar } from "./useInstanceShortcutBar";
import { CssBlockCube } from "../../../game/shared/BlockPreview";
import { BlockHoverTooltip } from "./BlockHoverTooltip";

export function InstanceShortcutBarSlot({
  shortcutBar,
  idx,
  slotBlock,
  getOrdinal,
  blockDefs,
  getPreview,
  blockDefsReady,
  hovered,
  onHoverEnter,
  onHoverLeave,
}: {
  shortcutBar: ReturnType<typeof useInstanceShortcutBar>;
  idx: number;
  slotBlock: string | null;
  getOrdinal: (name: string) => number | null;
  blockDefs: BlockInfoDto[];
  getPreview: (ordinal: number) => string | null;
  blockDefsReady: boolean;
  hovered: boolean;
  onHoverEnter: () => void;
  onHoverLeave: () => void;
}) {
  const isBreakSlot = idx === 0;
  const isSelected = shortcutBar.selectedSlot === idx;
  const isDropTarget = shortcutBar.dragOver === idx;
  const ordinal = !isBreakSlot && slotBlock ? getOrdinal(slotBlock) : null;
  const slotBlockInfo = slotBlock ? blockDefs.find((b) => b.name === slotBlock) : null;

  return (
    <div
      onClick={() => shortcutBar.selectSlot(idx)}
      onDragOver={(e) => shortcutBar.handleDragOver(e, idx)}
      onDragLeave={shortcutBar.handleDragLeave}
      onDrop={(e) => shortcutBar.handleDrop(e, idx)}
      onContextMenu={(e) => shortcutBar.handleContextMenu(e, idx)}
      onMouseEnter={onHoverEnter}
      onMouseLeave={onHoverLeave}
      title={isBreakSlot ? "Break" : (slotBlock ?? undefined)}
      className={`relative flex flex-col items-center gap-0.5 rounded border-2 p-1 w-14 cursor-pointer transition-colors ${
        isDropTarget ? "bg-white/20" : "bg-black/30"
      } ${isSelected ? "border-[#3C50E0]" : "border-transparent hover:border-white/20"}`}
    >
      <div className="absolute top-0 left-0.5 text-[#8A99AF] font-mono text-[7px]">
        {idx === 9 ? "0" : String(idx + 1)}
      </div>
      {isBreakSlot ? (
        <div className="text-sm">⛏</div>
      ) : slotBlock && ordinal !== null ? (
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
      <span className="text-[9px] leading-tight text-[#8A99AF] text-center truncate w-full">
        {slotBlockInfo?.name?.replace(/_/g, " ") ?? "-"}
      </span>
      {hovered && slotBlockInfo && <BlockHoverTooltip block={slotBlockInfo} ordinal={ordinal} above={false} />}
    </div>
  );
}
