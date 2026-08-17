import type { RefObject } from "react";
import { CodexCard } from "./CodexCard";
import { BbmodelAnimationViewer } from "../../../admin/components/BbmodelAnimationViewer";
import { CodexDetailLayout } from "./CodexDetailLayout";
import { CodexDetailRow } from "./CodexDetailRow";
import { animDisplayName, animEmoji } from "../../../lib/animationHelpers";
import type { AnimationEntry } from "../../../lib/animationHelpers";
import { filterInputStyle, filterWrapperStyle, gridStyle } from "./codexListStyles";

interface Props {
  animations: AnimationEntry[];
  filter: string;
  onFilterChange: (filter: string) => void;
  gridRef: RefObject<HTMLDivElement | null>;
  selectedKey: string | number | undefined;
  onSelect: (key: string) => void;
  registerRef: (key: string, el: HTMLDivElement | null) => void;
  allSkins: string[];
  selectedAnimSkin: string;
  onSelectAnimSkin: (skin: string) => void;
}

export function filterAnimations(animations: AnimationEntry[], filter: string): AnimationEntry[] {
  return animations.filter((a) => animDisplayName(a.fullName).toLowerCase().includes(filter.toLowerCase()));
}

export function AnimationList({
  animations,
  filter,
  onFilterChange,
  gridRef,
  selectedKey,
  onSelect,
  registerRef,
  allSkins,
  selectedAnimSkin,
  onSelectAnimSkin,
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
      {allSkins.length > 1 && (
        <div style={{ padding: "4px 12px 4px", flexShrink: 0, display: "flex", gap: 4, flexWrap: "wrap" }}>
          {allSkins.map((s) => (
            <button
              key={s}
              onClick={() => onSelectAnimSkin(s)}
              style={{
                background: selectedAnimSkin === s ? "#2a3a2a" : "#1e1e1e",
                border: `1px solid ${selectedAnimSkin === s ? "#7aac7a" : "#3a3a3a"}`,
                borderRadius: 4,
                color: selectedAnimSkin === s ? "#7aac7a" : "#888",
                fontFamily: "monospace",
                fontSize: 11,
                padding: "3px 8px",
                cursor: "pointer",
              }}
            >
              {s}
            </button>
          ))}
        </div>
      )}
      <div ref={gridRef} style={gridStyle}>
        {filterAnimations(animations, filter).map((anim) => {
          const display = animDisplayName(anim.fullName);
          return (
            <CodexCard
              key={anim.fullName}
              ref={(el) => registerRef(anim.fullName, el)}
              selected={selectedKey === anim.fullName}
              onClick={() => onSelect(anim.fullName)}
              title={display}
              label={display}
              width={80}
              padding="6px 4px"
              gap={2}
              labelFontSize={9}
              thumbnail={
                <div
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: 6,
                    background: "#1e1e1e",
                    border: "1px solid #333",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: 18,
                  }}
                >
                  {animEmoji(anim.fullName)}
                </div>
              }
              extra={<span style={{ fontSize: 8, color: "#555" }}>{anim.length.toFixed(2)}s</span>}
            />
          );
        })}
      </div>
    </>
  );
}

// eslint-disable-next-line react/no-multi-comp -- co-located with AnimationList per codex list/detail pairing convention
export function AnimationDetail({ anim, skin }: { anim: AnimationEntry; skin: string }) {
  const bbmodel = window.mcState?.playerBbmodels?.[skin] ?? null;

  return (
    <CodexDetailLayout
      preview={<BbmodelAnimationViewer bbmodel={bbmodel} animFullName={anim.fullName} width={160} height={220} />}
      title={animDisplayName(anim.fullName)}
      titleFontSize={13}
    >
      <div>
        <CodexDetailRow label="Durée" value={`${anim.length.toFixed(3)} s`} />
        <CodexDetailRow label="Os animés" value={anim.boneCount} />
      </div>
    </CodexDetailLayout>
  );
}
