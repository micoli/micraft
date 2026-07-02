import { useState, useEffect, useRef } from "react";
import { PlayerModelPreview } from "../shared/PlayerModelPreview";
import { Dialog, DialogContent, DialogTitle } from "../primitives/Dialog";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "../primitives/Tabs";
import { Button } from "../primitives/Button";
import { cn } from "../primitives/cn";

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

interface Props {
  open: boolean;
  onClose: () => void;
  onCommand: (cmd: string) => void;
}

export function Character({ open, onClose, onCommand }: Props) {
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

  const sortedArmors = Object.keys(available).sort();

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="min-w-[520px] font-mono p-9">
        <DialogTitle className="text-blue-300 tracking-widest mb-5">CHARACTER</DialogTitle>

        <Tabs defaultValue="equipment">
          <TabsList className="mb-5">
            <TabsTrigger value="equipment" className="font-mono text-xs">
              Equipment
            </TabsTrigger>
          </TabsList>

          <TabsContent value="equipment">
            <div className="flex gap-9 items-start">
              {/* Armor list */}
              <div className="flex-1 min-w-[240px]">
                {sortedArmors.length === 0 && (
                  <div className="text-white/30 text-xs">No armor available.</div>
                )}
                {sortedArmors.map((name) => {
                  const slots = available[name];
                  const isEquipped = equipped.includes(name);
                  return (
                    <div
                      key={name}
                      className={cn(
                        "flex items-center gap-3 px-3 py-3 mb-2.5 rounded border",
                        isEquipped
                          ? "bg-green-950/60 border-green-700/60"
                          : "bg-black/40 border-white/15",
                      )}
                    >
                      <div className="flex-1">
                        <div
                          className={cn(
                            "text-xs mb-1.5",
                            isEquipped ? "text-green-400" : "text-white/80",
                          )}
                        >
                          {name}
                        </div>
                        <div className="flex gap-1 flex-wrap">
                          {SLOT_LABELS.filter((s) => slots[s.key]).map((s) => (
                            <span
                              key={s.key}
                              className="text-[9px] px-1 py-px bg-blue-950/60 border border-blue-700/40 rounded-sm text-blue-300"
                            >
                              {s.label}
                            </span>
                          ))}
                        </div>
                      </div>
                      <Button
                        variant={isEquipped ? "ghost" : "secondary"}
                        size="sm"
                        onClick={() => toggleArmor(name)}
                        className={cn(
                          "font-mono text-xs whitespace-nowrap",
                          isEquipped && "text-green-400 hover:text-red-400",
                        )}
                      >
                        {isEquipped ? "Unequip" : "Equip"}
                      </Button>
                    </div>
                  );
                })}

                <Button
                  variant="ghost"
                  onClick={onClose}
                  className="mt-6 w-full font-mono text-xs text-white/50"
                >
                  Close
                </Button>
              </div>

              {/* Model preview */}
              <div className="flex flex-col items-center gap-2">
                <PlayerModelPreview
                  key={skin + equipped.join(",")}
                  skin={skin}
                  armors={equipped}
                  walking={walking}
                />
                <div className="flex gap-1 w-40">
                  <button
                    onClick={() => setWalking(false)}
                    className={cn(
                      "flex-1 font-mono text-[11px] py-1 rounded border transition-colors",
                      !walking
                        ? "bg-green-950/60 border-green-700/60 text-green-400"
                        : "bg-black/20 border-white/15 text-white/40",
                    )}
                  >
                    Statique
                  </button>
                  <button
                    onClick={() => setWalking(true)}
                    className={cn(
                      "flex-1 font-mono text-[11px] py-1 rounded border transition-colors",
                      walking
                        ? "bg-green-950/60 border-green-700/60 text-green-400"
                        : "bg-black/20 border-white/15 text-white/40",
                    )}
                  >
                    Marche
                  </button>
                </div>
              </div>
            </div>
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
