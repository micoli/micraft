import { useEffect } from "react";

export interface NpcDialogData {
  type: string;
  name: string;
}

interface Props {
  data: NpcDialogData | null;
  onClose: () => void;
}

export function NpcDialog({ data, onClose }: Props) {
  useEffect(() => {
    if (!data) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [data, onClose]);

  if (!data) return null;

  const overlayStyle: React.CSSProperties = {
    position: "fixed",
    inset: 0,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background: "rgba(0,0,0,0.45)",
    zIndex: 5000,
  };
  const boxStyle: React.CSSProperties = {
    background: "#1a1a1a",
    border: "2px solid #555",
    borderRadius: 8,
    padding: "24px 32px",
    minWidth: 260,
    color: "#eee",
    fontFamily: "monospace",
    boxShadow: "0 8px 32px rgba(0,0,0,0.7)",
  };
  const titleStyle: React.CSSProperties = {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 8,
  };
  const typeStyle: React.CSSProperties = {
    fontSize: 13,
    color: "#aaa",
    marginBottom: 20,
  };
  const btnStyle: React.CSSProperties = {
    padding: "6px 18px",
    background: "#444",
    border: "1px solid #666",
    borderRadius: 4,
    color: "#eee",
    cursor: "pointer",
    fontFamily: "monospace",
  };

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={boxStyle} onClick={(e) => e.stopPropagation()}>
        <div style={titleStyle}>{data.name}</div>
        <div style={typeStyle}>{data.type}</div>
        <button style={btnStyle} onClick={onClose}>
          Close
        </button>
      </div>
    </div>
  );
}
