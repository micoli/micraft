import { type BlockInfoDto } from "../../../api";
import { CssBlockCube } from "../../../../game/shared/BlockPreview";
import { BlockHoverTooltip } from "./BlockHoverTooltip";

function rgbToHex([r, g, b]: [number, number, number]): string {
  return `#${[r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("")}`;
}

export function VoxelPaletteBlock({
  block,
  ordinal,
  selected,
  getPreview,
  blockDefsReady,
  previewsReady,
  hovered,
  anchorRect,
  onClick,
  onMouseEnter,
  onMouseLeave,
}: {
  block: BlockInfoDto;
  ordinal: number | null;
  selected: boolean;
  getPreview: (ordinal: number) => string | null;
  blockDefsReady: boolean;
  previewsReady: boolean;
  hovered: boolean;
  anchorRect: DOMRect | null;
  onClick: () => void;
  onMouseEnter: (e: React.MouseEvent<HTMLButtonElement>) => void;
  onMouseLeave: () => void;
}) {
  return (
    <button
      draggable
      onDragStart={(e) => e.dataTransfer.setData("text/plain", block.name)}
      onClick={onClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      className={`relative flex flex-col items-center gap-0.5 rounded border-2 p-1 w-14 ${
        selected ? "border-[#3C50E0]" : "border-transparent hover:border-white/20"
      }`}
    >
      {!previewsReady ? (
        <div className="w-5 h-5 rounded" style={{ background: rgbToHex(block.minimapColor) }} />
      ) : ordinal !== null && getPreview(ordinal) ? (
        <img
          alt=""
          src={getPreview(ordinal)!}
          width={20}
          height={20}
          style={{ imageRendering: "pixelated", display: "block" }}
        />
      ) : blockDefsReady && ordinal !== null ? (
        <CssBlockCube ordinal={ordinal} size={20} />
      ) : (
        <div className="w-5 h-5 rounded" style={{ background: rgbToHex(block.minimapColor) }} />
      )}
      <span className="text-[9px] leading-tight text-[#8A99AF] text-center truncate w-full">
        {block.name.replace(/_/g, " ")}
      </span>
      {hovered && anchorRect && (
        <BlockHoverTooltip block={block} ordinal={ordinal} anchorRect={anchorRect} previewsReady={previewsReady} />
      )}
    </button>
  );
}
