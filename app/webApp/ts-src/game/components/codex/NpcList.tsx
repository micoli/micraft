import type { RefObject } from "react";
import { CodexCard } from "./CodexCard";
import { EmojiThumbnail } from "./EmojiThumbnail";
import { Npc3DPreview } from "./Npc3DPreview";
import { CodexDetailLayout } from "./CodexDetailLayout";
import { CodexDetailRow } from "./CodexDetailRow";
import type { NpcEntry } from "./CodexModal";
import { filterInputStyle, filterWrapperStyle, gridStyle } from "./codexListStyles";

const BEHAVIOR_EMOJI: Record<string, string> = {
  interactionable: "💬",
  random_movable: "🐾",
  static: "🗿",
};

const BEHAVIOR_LABEL: Record<string, string> = {
  interactionable: "PNJ interactif",
  random_movable: "Vagabond",
  static: "Statique",
};

interface Props {
  npcs: NpcEntry[];
  filter: string;
  onFilterChange: (filter: string) => void;
  gridRef: RefObject<HTMLDivElement | null>;
  selectedKey: string | number | undefined;
  onSelect: (key: string) => void;
  registerRef: (key: string, el: HTMLDivElement | null) => void;
}

export function filterNpcs(npcs: NpcEntry[], filter: string): NpcEntry[] {
  return npcs.filter((n) => n.type.toLowerCase().includes(filter.toLowerCase()));
}

export function NpcList({ npcs, filter, onFilterChange, gridRef, selectedKey, onSelect, registerRef }: Props) {
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
        {filterNpcs(npcs, filter).map((npc) => (
          <CodexCard
            key={npc.type}
            ref={(el) => registerRef(npc.type, el)}
            selected={selectedKey === npc.type}
            onClick={() => onSelect(npc.type)}
            title={npc.type}
            label={npc.type.replace(/_/g, " ")}
            thumbnail={<EmojiThumbnail emoji={BEHAVIOR_EMOJI[npc.behaviorKey] ?? "?"} />}
          />
        ))}
      </div>
    </>
  );
}

// eslint-disable-next-line react/no-multi-comp -- co-located with NpcList per codex list/detail pairing convention
export function NpcDetail({ npc }: { npc: NpcEntry }) {
  return (
    <CodexDetailLayout preview={<Npc3DPreview npc={npc} />} title={npc.type.replace(/_/g, " ")}>
      <div>
        <CodexDetailRow label="Comportement" value={BEHAVIOR_LABEL[npc.behaviorKey] ?? npc.behaviorKey} />
        <CodexDetailRow label="Taille" value={`${npc.width.toFixed(1)} × ${npc.height.toFixed(1)}`} />
        <CodexDetailRow label="Spawn auto" value={npc.autoSpawn ? "oui" : "non"} />
        {npc.wanderSpeed > 0 ? <CodexDetailRow label="Vitesse" value={npc.wanderSpeed.toFixed(1)} /> : null}
      </div>
    </CodexDetailLayout>
  );
}
