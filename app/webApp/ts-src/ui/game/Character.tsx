import { useState, useEffect, useRef } from "react";
import { PlayerModelPreview } from "../shared/PlayerModelPreview";

interface ArmorSlots {
  head: boolean;
  body: boolean;
  rightArm: boolean;
  leftArm: boolean;
  rightLeg: boolean;
  leftLeg: boolean;
}

const SLOT_LABELS: { key: keyof ArmorSlots; label: string }[] = [
  { key: "head", label: "HEAD" },
  { key: "body", label: "BODY" },
  { key: "rightArm", label: "R.ARM" },
  { key: "leftArm", label: "L.ARM" },
  { key: "rightLeg", label: "R.LEG" },
  { key: "leftLeg", label: "L.LEG" },
];

function slotsOverlap(a: ArmorSlots | undefined, b: ArmorSlots | undefined): boolean {
  if (!a || !b) return false;
  return (
    (a.head && b.head) ||
    (a.body && b.body) ||
    (a.rightArm && b.rightArm) ||
    (a.leftArm && b.leftArm) ||
    (a.rightLeg && b.rightLeg) ||
    (a.leftLeg && b.leftLeg)
  );
}

type Tab = "equipment";

const TABS: { id: Tab; label: string }[] = [{ id: "equipment", label: "Equipment" }];

interface Props {
  open: boolean;
  onClose: () => void;
  onCommand: (cmd: string) => void;
}

export function Character({ open, onClose, onCommand }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>("equipment");
  const [available, setAvailable] = useState<Record<string, ArmorSlots>>({});
  const [equipped, setEquipped] = useState<string[]>([]);
  const [skin, setSkin] = useState("player");
  const [walking, setWalking] = useState(true);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const playerName = window.mcState?.playerName || "";
    Promise.all([
      fetch("/api/armors").then((r) => r.json()),
      fetch(`/api/player/${encodeURIComponent(playerName)}/armors`)
        .then((r) => r.json())
        .catch(() => []),
      fetch(`/api/player/${encodeURIComponent(playerName)}/skin`)
        .then((r) => r.json())
        .catch(() => ({ skin: "player" })),
    ]).then(([armors, equippedArmors, skinData]) => {
      setAvailable(armors as Record<string, ArmorSlots>);
      setEquipped(Array.isArray(equippedArmors) ? equippedArmors : []);
      setSkin((skinData as { skin: string }).skin ?? "player");
    });
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") closeRef.current();
    }
    document.addEventListener("keydown", onKey as unknown as EventListener);
    return () => document.removeEventListener("keydown", onKey as unknown as EventListener);
  }, [open]);

  function toggleArmor(name: string) {
    if (equipped.includes(name)) {
      onCommand(`/unequip ${name}`);
      setEquipped((prev) => prev.filter((a) => a !== name));
    } else {
      const slots = available[name];
      const conflicting = equipped.filter((a) => slotsOverlap(available[a], slots));
      conflicting.forEach((c) => onCommand(`/unequip ${c}`));
      onCommand(`/equip ${name}`);
      setEquipped((prev) => prev.filter((a) => !conflicting.includes(a)).concat(name));
    }
  }

  if (!open) return null;

  const sortedArmors = Object.keys(available).sort();

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.72)",
        zIndex: 1100,
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: "#1a1a1a",
          border: "1px solid #444",
          borderRadius: 8,
          padding: "24px 28px",
          display: "flex",
          flexDirection: "column",
          gap: 0,
          fontFamily: "monospace",
          color: "#eee",
          minWidth: 520,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Title */}
        <div style={{ fontSize: 16, fontWeight: "bold", color: "#6af", marginBottom: 16, letterSpacing: 2 }}>
          CHARACTER
        </div>

        {/* Tab bar */}
        <div style={{ display: "flex", gap: 0, marginBottom: 16, borderBottom: "1px solid #333" }}>
          {TABS.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                padding: "6px 16px",
                background: "transparent",
                border: "none",
                borderBottom: `2px solid ${activeTab === tab.id ? "#6af" : "transparent"}`,
                color: activeTab === tab.id ? "#6af" : "#777",
                fontFamily: "monospace",
                fontSize: 13,
                cursor: "pointer",
                marginBottom: -1,
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Body: tab content + preview side by side */}
        <div style={{ display: "flex", gap: 28, alignItems: "flex-start" }}>
          {/* Tab content */}
          <div style={{ flex: 1, minWidth: 240 }}>
            {activeTab === "equipment" && (
              <>
                {sortedArmors.length === 0 && <div style={{ color: "#555", fontSize: 13 }}>No armor available.</div>}
                {sortedArmors.map((name) => {
                  const slots = available[name];
                  const isEquipped = equipped.includes(name);
                  return (
                    <div
                      key={name}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 10,
                        padding: "8px 10px",
                        marginBottom: 6,
                        background: isEquipped ? "#1e2e1e" : "#111",
                        border: `1px solid ${isEquipped ? "#3a6a3a" : "#333"}`,
                        borderRadius: 4,
                      }}
                    >
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 13, color: isEquipped ? "#7aac7a" : "#ccc", marginBottom: 4 }}>
                          {name}
                        </div>
                        <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                          {SLOT_LABELS.filter((s) => slots[s.key]).map((s) => (
                            <span
                              key={s.key}
                              style={{
                                fontSize: 9,
                                padding: "1px 4px",
                                background: "#2a2a4a",
                                border: "1px solid #445",
                                borderRadius: 3,
                                color: "#88f",
                              }}
                            >
                              {s.label}
                            </span>
                          ))}
                        </div>
                      </div>
                      <button
                        onClick={() => toggleArmor(name)}
                        style={{
                          padding: "5px 10px",
                          background: isEquipped ? "#2a3d2a" : "#222",
                          border: `1px solid ${isEquipped ? "#4a7a4a" : "#555"}`,
                          borderRadius: 4,
                          color: isEquipped ? "#7aac7a" : "#aaa",
                          fontFamily: "monospace",
                          fontSize: 12,
                          cursor: "pointer",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {isEquipped ? "Unequip" : "Equip"}
                      </button>
                    </div>
                  );
                })}
              </>
            )}

            <button
              onClick={onClose}
              style={{
                marginTop: 16,
                width: "100%",
                padding: "7px 0",
                background: "transparent",
                border: "1px solid #444",
                borderRadius: 4,
                color: "#888",
                fontFamily: "monospace",
                fontSize: 13,
                cursor: "pointer",
              }}
            >
              Close
            </button>
          </div>

          {/* Preview */}
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
            <PlayerModelPreview key={skin + equipped.join(",")} skin={skin} armors={equipped} walking={walking} />
            <div style={{ display: "flex", gap: 4, width: 160 }}>
              <button
                onClick={() => setWalking(false)}
                style={{
                  flex: 1,
                  background: !walking ? "#2a3d2a" : "#1e1e1e",
                  border: `1px solid ${!walking ? "#4a7a4a" : "#333"}`,
                  borderRadius: 4,
                  color: !walking ? "#7aac7a" : "#666",
                  fontFamily: "monospace",
                  fontSize: 11,
                  cursor: "pointer",
                  padding: "4px 0",
                }}
              >
                Statique
              </button>
              <button
                onClick={() => setWalking(true)}
                style={{
                  flex: 1,
                  background: walking ? "#2a3d2a" : "#1e1e1e",
                  border: `1px solid ${walking ? "#4a7a4a" : "#333"}`,
                  borderRadius: 4,
                  color: walking ? "#7aac7a" : "#666",
                  fontFamily: "monospace",
                  fontSize: 11,
                  cursor: "pointer",
                  padding: "4px 0",
                }}
              >
                Marche
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
