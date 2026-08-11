export function ItemSlot({
  type: _type,
  count,
  bg,
  label,
  onClick,
  style,
}: {
  type: string;
  count: number;
  bg: string;
  label: string;
  onClick?: () => void;
  style?: React.CSSProperties;
}) {
  return (
    <div
      onClick={onClick}
      title={`${label} ×${count}`}
      style={{
        width: 56,
        height: 56,
        background: bg,
        border: "2px solid rgba(255,255,255,0.25)",
        borderRadius: 4,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        cursor: onClick ? "pointer" : "default",
        userSelect: "none",
        flexShrink: 0,
        ...style,
      }}
    >
      <span style={{ fontSize: 11, color: "#fff", fontWeight: 600, textAlign: "center", lineHeight: 1.1 }}>
        - {label}
      </span>
      <span style={{ fontSize: 13, color: "#ffd", fontWeight: 700 }}>×{count}</span>
    </div>
  );
}
