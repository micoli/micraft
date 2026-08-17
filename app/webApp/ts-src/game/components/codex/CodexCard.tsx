import { forwardRef } from "react";
import type { ReactNode } from "react";

interface Props {
  selected: boolean;
  onClick: () => void;
  title: string;
  label: string;
  width?: number;
  padding?: string;
  gap?: number;
  labelFontSize?: number;
  thumbnail: ReactNode;
  extra?: ReactNode;
}

export const CodexCard = forwardRef<HTMLDivElement, Props>(function CodexCard(
  { selected, onClick, title, label, width = 90, padding = "8px 6px", gap = 4, labelFontSize = 11, thumbnail, extra },
  ref,
) {
  const cardStyle: React.CSSProperties = {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    padding,
    cursor: "pointer",
    borderRadius: 6,
    border: `2px solid ${selected ? "#7aac7a" : "transparent"}`,
    background: selected ? "rgba(122,172,122,0.12)" : "transparent",
    gap,
    width,
  };

  return (
    <div ref={ref} style={cardStyle} onClick={onClick} title={title}>
      {thumbnail}
      <span
        style={{ fontSize: labelFontSize, color: "#ccc", textAlign: "center", wordBreak: "break-all", lineHeight: 1.2 }}
      >
        {label}
      </span>
      {extra}
    </div>
  );
});
