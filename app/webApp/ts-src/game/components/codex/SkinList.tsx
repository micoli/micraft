import { useState } from "react";
import type { RefObject } from "react";
import { CodexCard } from "./CodexCard";
import { EmojiThumbnail } from "./EmojiThumbnail";
import { SkinModelPreview } from "../SkinModelPreview";
import { CodexDetailLayout } from "./CodexDetailLayout";
import { filterInputStyle, filterWrapperStyle, gridStyle } from "./codexListStyles";

interface Props {
  skins: string[];
  filter: string;
  onFilterChange: (filter: string) => void;
  gridRef: RefObject<HTMLDivElement | null>;
  selectedKey: string | number | undefined;
  onSelect: (key: string) => void;
  registerRef: (key: string, el: HTMLDivElement | null) => void;
}

export function filterSkins(skins: string[], filter: string): string[] {
  return skins.filter((s) => s.toLowerCase().includes(filter.toLowerCase()));
}

export function SkinList({ skins, filter, onFilterChange, gridRef, selectedKey, onSelect, registerRef }: Props) {
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
        {filterSkins(skins, filter).map((skin) => (
          <CodexCard
            key={skin}
            ref={(el) => registerRef(skin, el)}
            selected={selectedKey === skin}
            onClick={() => onSelect(skin)}
            title={skin}
            label={skin.replace(/_/g, " ")}
            thumbnail={<EmojiThumbnail emoji="🧑" />}
          />
        ))}
      </div>
    </>
  );
}

// eslint-disable-next-line react/no-multi-comp -- co-located with SkinList per codex list/detail pairing convention
export function SkinDetail({ name }: { name: string }) {
  const [walking, setWalking] = useState(true);

  const btnStyle = (active: boolean): React.CSSProperties => ({
    flex: 1,
    background: active ? "#2a3d2a" : "#1e1e1e",
    border: `1px solid ${active ? "#4a7a4a" : "#333"}`,
    borderRadius: 4,
    color: active ? "#7aac7a" : "#666",
    fontFamily: "monospace",
    fontSize: 11,
    cursor: "pointer",
    padding: "4px 0",
  });

  return (
    <CodexDetailLayout preview={<SkinModelPreview skin={name} walking={walking} />} title={name.replace(/_/g, " ")}>
      <div style={{ display: "flex", gap: 4 }}>
        <button style={btnStyle(!walking)} onClick={() => setWalking(false)}>
          Statique
        </button>
        <button style={btnStyle(walking)} onClick={() => setWalking(true)}>
          Marche
        </button>
      </div>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <button
          onClick={() => window.mcState.events.push(`cmd:/skin ${name}`)}
          style={{
            background: "#2a3d2a",
            border: "1px solid #4a7a4a",
            borderRadius: 4,
            color: "#7aac7a",
            fontFamily: "monospace",
            fontSize: 12,
            cursor: "pointer",
            padding: "5px 16px",
          }}
        >
          Équiper
        </button>
      </div>
    </CodexDetailLayout>
  );
}
