import { forwardRef } from "react";
import type { NpcEntry } from "./CodexModal";

export const NpcCard = forwardRef<HTMLDivElement, { npc: NpcEntry; selected: boolean; onClick: () => void }>(
  function NpcCard({ npc, selected, onClick }, ref) {
    const behaviorEmoji: Record<string, string> = {
      interactionable: "💬",
      random_movable: "🐾",
      static: "🗿",
    };
    const emoji = behaviorEmoji[npc.behaviorKey] ?? "?";

    const cardStyle: React.CSSProperties = {
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      padding: "8px 6px",
      cursor: "pointer",
      borderRadius: 6,
      border: `2px solid ${selected ? "#7aac7a" : "transparent"}`,
      background: selected ? "rgba(122,172,122,0.12)" : "transparent",
      gap: 4,
      width: 90,
    };

    return (
      <div ref={ref} style={cardStyle} onClick={onClick} title={npc.type}>
        <div
          style={{
            width: 52,
            height: 52,
            background: "#2a2a2a",
            borderRadius: 8,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 28,
            border: "1px solid #444",
          }}
        >
          {emoji}
        </div>
        <span style={{ fontSize: 11, color: "#ccc", textAlign: "center", lineHeight: 1.2 }}>
          {npc.type.replace(/_/g, " ")}
        </span>
      </div>
    );
  },
);
