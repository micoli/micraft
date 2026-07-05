import { useState, useCallback, useRef, useEffect } from "react";
import { Dialog, DialogContent, DialogTitle } from "../primitives/Dialog";
import { Button } from "../primitives/Button";

interface Props {
  open: boolean;
  tradeId: string | null;
  otherPlayer: string;
  myOffer: Record<string, number>;
  theirOffer: Record<string, number>;
  myAccepted: boolean;
  theirAccepted: boolean;
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  onClose: (tradeId: string | null) => void;
  onAccept: (tradeId: string) => void;
  onOffer: (tradeId: string, offer: Record<string, number>) => void;
}

function ItemSlot({
  type,
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
        {label}
      </span>
      <span style={{ fontSize: 13, color: "#ffd", fontWeight: 700 }}>×{count}</span>
    </div>
  );
}

export function Trade({
  open,
  tradeId,
  otherPlayer,
  theirOffer,
  myAccepted,
  theirAccepted,
  inventory,
  itemMeta,
  onClose,
  onAccept,
  onOffer,
}: Props) {
  // localOffer: what this player has built, displayed optimistically
  const [localOffer, setLocalOffer] = useState<Record<string, number>>({});
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Reset local state when a new trade opens
  useEffect(() => {
    if (open) setLocalOffer({});
  }, [tradeId, open]);

  const sendOffer = useCallback(
    (offer: Record<string, number>) => {
      if (!tradeId) return;
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => onOffer(tradeId, offer), 300);
    },
    [tradeId, onOffer],
  );

  const addItem = useCallback(
    (type: string) => {
      const available = inventory[type] ?? 0;
      const current = localOffer[type] ?? 0;
      if (current >= available) return;
      const next = { ...localOffer, [type]: current + 1 };
      setLocalOffer(next);
      sendOffer(next);
    },
    [inventory, localOffer, sendOffer],
  );

  const removeItem = useCallback(
    (type: string) => {
      const current = localOffer[type] ?? 0;
      if (current <= 0) return;
      const next = { ...localOffer };
      if (current === 1) delete next[type];
      else next[type] = current - 1;
      setLocalOffer(next);
      sendOffer(next);
    },
    [localOffer, sendOffer],
  );

  const handleAccept = useCallback(() => {
    if (!tradeId) return;
    onAccept(tradeId);
  }, [tradeId, onAccept]);

  const handleClose = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    setLocalOffer({});
    onClose(tradeId);
  }, [tradeId, onClose]);

  const myOfferEntries = Object.entries(localOffer).filter(([, v]) => v > 0);
  const theirOfferEntries = Object.entries(theirOffer).filter(([, v]) => v > 0);
  const inventoryItems = Object.entries(inventory).filter(([, v]) => v > 0);

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) handleClose();
      }}
    >
      <DialogContent className="w-[680px] max-w-[95vw]" movable>
        <DialogTitle>Trade with {otherPlayer}</DialogTitle>

        <div style={{ display: "flex", gap: 16, marginTop: 16 }}>
          {/* Your offer */}
          <div style={{ flex: 1 }}>
            <div style={{ color: "rgba(255,255,255,0.6)", fontSize: 12, marginBottom: 6 }}>
              Your offer {myAccepted && <span style={{ color: "#4ade80" }}>✓ Accepted</span>}
            </div>
            <div
              style={{
                minHeight: 80,
                border: "1px solid rgba(255,255,255,0.15)",
                borderRadius: 6,
                padding: 8,
                display: "flex",
                flexWrap: "wrap",
                gap: 6,
                marginBottom: 8,
              }}
            >
              {myOfferEntries.length === 0 && (
                <span style={{ color: "rgba(255,255,255,0.3)", fontSize: 12, alignSelf: "center" }}>
                  Nothing offered yet
                </span>
              )}
              {myOfferEntries.map(([type, count]) => {
                const meta = itemMeta[type] ?? { label: type, bg: "#555" };
                return (
                  <div key={type} style={{ position: "relative" }}>
                    <ItemSlot type={type} count={count} bg={meta.bg} label={meta.label} />
                    <button
                      onClick={() => removeItem(type)}
                      title="Remove one"
                      style={{
                        position: "absolute",
                        top: -6,
                        right: -6,
                        background: "#ef4444",
                        border: "none",
                        borderRadius: "50%",
                        width: 18,
                        height: 18,
                        color: "#fff",
                        cursor: "pointer",
                        fontSize: 14,
                        lineHeight: 1,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        padding: 0,
                      }}
                    >
                      −
                    </button>
                  </div>
                );
              })}
            </div>

            <div style={{ color: "rgba(255,255,255,0.5)", fontSize: 11, marginBottom: 4 }}>
              Your inventory — click to add
            </div>
            <div
              style={{
                maxHeight: 140,
                overflowY: "auto",
                border: "1px solid rgba(255,255,255,0.1)",
                borderRadius: 6,
                padding: 6,
                display: "flex",
                flexWrap: "wrap",
                gap: 4,
              }}
            >
              {inventoryItems.map(([type, total]) => {
                const meta = itemMeta[type] ?? { label: type, bg: "#555" };
                const inOffer = localOffer[type] ?? 0;
                const remaining = total - inOffer;
                return (
                  <ItemSlot
                    key={type}
                    type={type}
                    count={remaining}
                    bg={meta.bg}
                    label={meta.label}
                    onClick={() => addItem(type)}
                    style={{
                      opacity: remaining <= 0 ? 0.35 : 1,
                      border: `2px solid ${inOffer > 0 ? "#4ade80" : "rgba(255,255,255,0.25)"}`,
                    }}
                  />
                );
              })}
              {inventoryItems.length === 0 && (
                <span style={{ color: "rgba(255,255,255,0.3)", fontSize: 12 }}>Empty inventory</span>
              )}
            </div>
          </div>

          <div style={{ width: 1, background: "rgba(255,255,255,0.1)", alignSelf: "stretch" }} />

          {/* Their offer */}
          <div style={{ flex: 1 }}>
            <div style={{ color: "rgba(255,255,255,0.6)", fontSize: 12, marginBottom: 6 }}>
              {otherPlayer}'s offer {theirAccepted && <span style={{ color: "#4ade80" }}>✓ Accepted</span>}
            </div>
            <div
              style={{
                minHeight: 80,
                border: "1px solid rgba(255,255,255,0.15)",
                borderRadius: 6,
                padding: 8,
                display: "flex",
                flexWrap: "wrap",
                gap: 6,
              }}
            >
              {theirOfferEntries.length === 0 && (
                <span style={{ color: "rgba(255,255,255,0.3)", fontSize: 12, alignSelf: "center" }}>Waiting…</span>
              )}
              {theirOfferEntries.map(([type, count]) => {
                const meta = itemMeta[type] ?? { label: type, bg: "#555" };
                return <ItemSlot key={type} type={type} count={count} bg={meta.bg} label={meta.label} />;
              })}
            </div>
          </div>
        </div>

        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginTop: 16,
            paddingTop: 12,
            borderTop: "1px solid rgba(255,255,255,0.1)",
          }}
        >
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.5)" }}>
            {myAccepted && theirAccepted
              ? "Both accepted — executing…"
              : myAccepted
                ? `Waiting for ${otherPlayer}…`
                : theirAccepted
                  ? `${otherPlayer} accepted — confirm your offer`
                  : "Both players must accept to complete"}
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <Button variant="secondary" onClick={handleClose}>
              Cancel
            </Button>
            <Button variant="primary" disabled={myAccepted} onClick={handleAccept}>
              {myAccepted ? "Accepted ✓" : "Accept"}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
