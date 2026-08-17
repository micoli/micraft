import type { RefObject } from "react";
import { CodexCard } from "./CodexCard";
import { CssBlockCube } from "../../shared/BlockPreview";
import { Block3DPreview } from "../../shared/Block3DPreview";
import { plainColorHex } from "../../lib/blockDefs";
import { CodexDetailLayout } from "./CodexDetailLayout";
import { CodexDetailRow } from "./CodexDetailRow";
import type { BlockEntry, ItemEntry } from "./CodexModal";
import { filterInputStyle, filterWrapperStyle, gridStyle } from "./codexListStyles";

interface Props {
  items: ItemEntry[];
  blocks: BlockEntry[];
  filter: string;
  onFilterChange: (filter: string) => void;
  gridRef: RefObject<HTMLDivElement | null>;
  selectedKey: string | number | undefined;
  onSelect: (key: string) => void;
  registerRef: (key: string, el: HTMLDivElement | null) => void;
  defsReady: boolean;
  getPreview: (o: number) => string | null;
}

export function filterItems(items: ItemEntry[], filter: string): ItemEntry[] {
  return items.filter((it) => it.name.toLowerCase().includes(filter.toLowerCase()));
}

export function ItemList({
  items,
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
        {filterItems(items, filter).map((item) => {
          const linkedBlock = item.placesBlock ? blocks.find((b) => b.name === item.placesBlock) : null;
          const preview = linkedBlock ? getPreview(linkedBlock.ordinal) : null;
          return (
            <CodexCard
              key={item.name}
              ref={(el) => registerRef(item.name, el)}
              selected={selectedKey === item.name}
              onClick={() => onSelect(item.name)}
              title={item.name}
              label={item.name.replace(/_/g, " ")}
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
                )
              }
            />
          );
        })}
      </div>
    </>
  );
}

// eslint-disable-next-line react/no-multi-comp -- co-located with ItemList per codex list/detail pairing convention
export function ItemDetail({ item, blocks, defsReady }: { item: ItemEntry; blocks: BlockEntry[]; defsReady: boolean }) {
  const linkedBlock = item.placesBlock ? blocks.find((b) => b.name === item.placesBlock) : null;
  const colorHex = plainColorHex(item.plainColor);

  return (
    <CodexDetailLayout
      preview={
        defsReady && linkedBlock ? (
          <Block3DPreview ordinal={linkedBlock.ordinal} colorHex={colorHex} />
        ) : (
          <div
            style={{
              width: 160,
              height: 160,
              background: "#1a1a1a",
              borderRadius: 6,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 60,
            }}
          >
            ✦
          </div>
        )
      }
      title={item.name.replace(/_/g, " ")}
    >
      <div>
        <CodexDetailRow label="Posable" value={item.buildable ? "oui" : "non"} />
        <CodexDetailRow label="Place le bloc" value={item.placesBlock ? item.placesBlock.replace(/_/g, " ") : "—"} />
        {item.plainColor ? (
          <CodexDetailRow label="Couleur" value={`${item.plainColor} (#${colorHex ?? "??????"})`} />
        ) : null}
      </div>
    </CodexDetailLayout>
  );
}
