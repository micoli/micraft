import { useState } from "react";
import type { RefObject } from "react";
import { CodexCard } from "./CodexCard";
import { CssBlockCube } from "../../shared/BlockPreview";
import { Block3DPreview } from "../../shared/Block3DPreview";
import { PLAIN_COLORABLE_PREVIEW_HEX } from "../../shared/blockPreviewCache";
import { CodexDetailLayout } from "./CodexDetailLayout";
import { CodexDetailRow } from "./CodexDetailRow";
import type { BlockEntry } from "./CodexModal";
import { filterInputStyle, filterWrapperStyle, gridStyle } from "./codexListStyles";

interface Props {
  blocks: BlockEntry[];
  filter: string;
  onFilterChange: (filter: string) => void;
  gridRef: RefObject<HTMLDivElement | null>;
  selectedKey: string | number | undefined;
  onSelect: (key: number) => void;
  registerRef: (key: number, el: HTMLDivElement | null) => void;
  defsReady: boolean;
  getPreview: (o: number) => string | null;
}

export function filterBlocks(blocks: BlockEntry[], filter: string): BlockEntry[] {
  return blocks.filter((b) => b.name.toLowerCase().includes(filter.toLowerCase()));
}

export function BlockList({
  blocks,
  filter,
  onFilterChange,
  gridRef,
  selectedKey,
  onSelect,
  registerRef,
  defsReady,
  getPreview,
}: Props) {
  return (
    <>
      <div style={filterWrapperStyle}>
        <input
          type="text"
          placeholder="Filtrer…"
          value={filter}
          onChange={(e) => onFilterChange(e.target.value)}
          style={filterInputStyle}
        />
      </div>
      <div ref={gridRef} style={gridStyle}>
        {filterBlocks(blocks, filter).map((block) => {
          const preview = getPreview(block.ordinal);
          return (
            <CodexCard
              key={block.name}
              ref={(el) => registerRef(block.ordinal, el)}
              selected={selectedKey === block.ordinal}
              onClick={() => onSelect(block.ordinal)}
              title={block.name}
              label={block.name.replace(/_/g, " ")}
              width={80}
              padding="6px 4px"
              gap={2}
              labelFontSize={10}
              thumbnail={
                preview ? (
                  <img
                    alt="preview"
                    src={preview}
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
                )
              }
            />
          );
        })}
      </div>
    </>
  );
}

// eslint-disable-next-line react/no-multi-comp -- co-located with BlockList per codex list/detail pairing convention
export function BlockDetail({
  block,
  defsReady,
  giveItemName,
}: {
  block: BlockEntry;
  defsReady: boolean;
  giveItemName: string | null;
}) {
  const [qty, setQty] = useState(1);

  return (
    <CodexDetailLayout
      preview={
        defsReady ? (
          <Block3DPreview
            ordinal={block.ordinal}
            colorHex={block.plainColorable ? PLAIN_COLORABLE_PREVIEW_HEX : undefined}
          />
        ) : (
          <div style={{ width: 160, height: 160, background: "#1a1a1a", borderRadius: 6 }} />
        )
      }
      title={block.name.replace(/_/g, " ")}
    >
      <div>
        <CodexDetailRow label="Dureté" value={block.hardness === -1 ? "∞" : block.hardness} />
        <CodexDetailRow label="Solide" value={block.solid ? "oui" : "non"} />
        <CodexDetailRow label="Transparent" value={block.transparent ? "oui" : "non"} />
        <CodexDetailRow label="Liquide" value={block.liquid ? "oui" : "non"} />
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "center", paddingTop: 4 }}>
        <input
          type="number"
          min={1}
          max={128}
          value={qty}
          disabled={!giveItemName}
          onChange={(e) => setQty(Math.max(1, Math.min(128, parseInt(e.target.value) || 1)))}
          style={{
            width: 56,
            background: "#1e1e1e",
            border: "1px solid #3a3a3a",
            borderRadius: 4,
            color: giveItemName ? "#ddd" : "#555",
            fontFamily: "monospace",
            fontSize: 12,
            padding: "4px 6px",
            outline: "none",
          }}
        />
        <button
          disabled={!giveItemName}
          onClick={() => giveItemName && window.mcState.events.push(`cmd:/give ${giveItemName} ${qty}`)}
          style={{
            flex: 1,
            background: giveItemName ? "#2a3d2a" : "#1e1e1e",
            border: `1px solid ${giveItemName ? "#4a7a4a" : "#2a2a2a"}`,
            borderRadius: 4,
            color: giveItemName ? "#7aac7a" : "#444",
            fontFamily: "monospace",
            fontSize: 12,
            cursor: giveItemName ? "pointer" : "default",
            padding: "4px 8px",
          }}
          title={giveItemName ? undefined : "Aucun item disponible pour ce bloc"}
        >
          Donner
        </button>
      </div>
    </CodexDetailLayout>
  );
}
