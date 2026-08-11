import { forwardRef } from "react";
import { animDisplayName, animEmoji } from "../../lib/animationHelpers";
import type { AnimationEntry } from "../../lib/animationHelpers";

export const AnimationCard = forwardRef<
  HTMLDivElement,
  { anim: AnimationEntry; selected: boolean; onClick: () => void }
>(function AnimationCard({ anim, selected, onClick }, ref) {
  const display = animDisplayName(anim.fullName);
  return (
    <div
      ref={ref}
      style={{
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
      }}
      onClick={onClick}
      title={display}
    >
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
      <span
        style={{
          fontSize: 9,
          color: "#ccc",
          textAlign: "center",
          wordBreak: "break-all",
          lineHeight: 1.2,
          maxHeight: 28,
          overflow: "hidden",
        }}
      >
        {display}
      </span>
      <span style={{ fontSize: 8, color: "#555" }}>{anim.length.toFixed(2)}s</span>
    </div>
  );
});
