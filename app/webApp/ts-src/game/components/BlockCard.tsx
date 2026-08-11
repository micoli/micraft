import { forwardRef } from "react";
import { CssBlockCube } from "../shared/BlockPreview";
import type { BlockEntry } from "./CodexModal";

export const BlockCard = forwardRef<
  HTMLDivElement,
  {
    block: BlockEntry;
    selected: boolean;
    defsReady: boolean;
    getPreview: (o: number) => string | null;
    onClick: () => void;
  }
>(function BlockCard({ block, selected, defsReady, getPreview, onClick }, ref) {
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
    <div ref={ref} style={cardStyle} onClick={onClick} title={block.name}>
      {getPreview(block.ordinal) ? (
        <img
          alt="preview"
          src={getPreview(block.ordinal)!}
          width={48}
          height={48}
          style={{ imageRendering: "pixelated", display: "block" }}
        />
      ) : defsReady ? (
        <CssBlockCube ordinal={block.ordinal} size={36} />
      ) : (
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 4,
            background: `rgb(${block.minimapColor[0]},${block.minimapColor[1]},${block.minimapColor[2]})`,
          }}
        />
      )}
      <span style={label}>{block.name.replace(/_/g, " ")}</span>
    </div>
  );
});
