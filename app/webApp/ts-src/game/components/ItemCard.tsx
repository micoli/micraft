import { forwardRef } from "react";
import { CssBlockCube } from "../shared/BlockPreview";
import type { BlockEntry, ItemEntry } from "./CodexModal";

export const ItemCard = forwardRef<
  HTMLDivElement,
  {
    item: ItemEntry;
    blocks: BlockEntry[];
    selected: boolean;
    defsReady: boolean;
    getPreview: (o: number) => string | null;
    onClick: () => void;
  }
>(function ItemCard({ item, blocks, selected, defsReady, getPreview, onClick }, ref) {
  const linkedBlock = item.placesBlock ? blocks.find((b) => b.name === item.placesBlock) : null;

  const cardStyle: React.CSSProperties = {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    padding: "6px 4px",
    cursor: "pointer",
    borderRadius: 6,
    border: `2px solid ${selected ? "#7aac7a" : "transparent"}`,
    background: selected ? "rgba(122,172,122,0.12)" : "transparent",
    gap: 2,
    width: 80,
  };
  const label: React.CSSProperties = {
    fontSize: 10,
    color: "#ccc",
    textAlign: "center",
    wordBreak: "break-all",
    lineHeight: 1.2,
  };

  return (
    <div ref={ref} style={cardStyle} onClick={onClick} title={item.name}>
      {linkedBlock && getPreview(linkedBlock.ordinal) ? (
        <img
          alt="preview"
          src={getPreview(linkedBlock.ordinal)!}
          width={48}
          height={48}
          style={{ imageRendering: "pixelated", display: "block" }}
        />
      ) : linkedBlock && defsReady ? (
        <CssBlockCube ordinal={linkedBlock.ordinal} size={36} />
      ) : (
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 4,
            background: linkedBlock
              ? `rgb(${linkedBlock.minimapColor[0]},${linkedBlock.minimapColor[1]},${linkedBlock.minimapColor[2]})`
              : "#6a5acd",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 18,
          }}
        >
          {!linkedBlock ? "✦" : ""}
        </div>
      )}
      <span style={label}>{item.name.replace(/_/g, " ")}</span>
    </div>
  );
});
