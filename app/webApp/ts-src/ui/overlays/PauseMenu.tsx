import React, { useRef, useEffect } from "react";

const overlayStyle: React.CSSProperties = {
  position: "fixed",
  inset: 0,
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  background: "rgba(0,0,0,0.65)",
  zIndex: 1000,
};

const cardStyle: React.CSSProperties = {
  background: "#1a1a1a",
  border: "1px solid #444",
  borderRadius: 8,
  padding: "2rem 3rem",
  display: "flex",
  flexDirection: "column",
  gap: "0.75rem",
  minWidth: 220,
};

const titleStyle: React.CSSProperties = {
  color: "#fff",
  fontFamily: "monospace",
  fontSize: 20,
  fontWeight: "bold",
  textAlign: "center",
  marginBottom: "0.5rem",
  letterSpacing: 4,
};

const btnStyle: React.CSSProperties = {
  background: "#2a2a2a",
  border: "1px solid #555",
  borderRadius: 4,
  color: "#ddd",
  fontFamily: "monospace",
  fontSize: 14,
  padding: "0.5rem 1rem",
  cursor: "pointer",
  textAlign: "center",
};

interface PauseMenuProps {
  open: boolean;
  onClose: () => void;
  onDisconnect: () => void;
  onPreferences: () => void;
}

export function PauseMenu({ open, onClose, onDisconnect, onPreferences }: PauseMenuProps) {
  const prefsButtonRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (open) setTimeout(() => prefsButtonRef.current?.focus(), 50);
  }, [open]);
  if (!open) return null;
  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={cardStyle} onClick={(e) => e.stopPropagation()}>
        <div style={titleStyle}>PAUSE</div>
        <button ref={prefsButtonRef} style={btnStyle} onClick={onPreferences}>
          Preferences
        </button>
        <button style={{ ...btnStyle, color: "#f88" }} onClick={onDisconnect}>
          Disconnect
        </button>
      </div>
    </div>
  );
}
