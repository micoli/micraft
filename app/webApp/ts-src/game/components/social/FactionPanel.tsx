import { Button } from "../../../primitives/Button";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { FactionSyncData } from "../../types";

interface Props {
  open: boolean;
  faction: FactionSyncData | null;
  onClose: () => void;
}

function emit(ev: string) {
  window.mcState?.events?.push(ev);
}

export function FactionPanel({ open, faction, onClose }: Props) {
  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="w-[380px] max-w-[95vw]" movable>
        <DialogTitle>Factions</DialogTitle>
        {!faction?.enabled ? (
          <p style={{ marginTop: 12, fontSize: 12, opacity: 0.7 }}>Les factions sont désactivées sur ce serveur.</p>
        ) : (
          <div style={{ marginTop: 12 }}>
            {faction.changeCooldownRemainingMs > 0 && (
              <p style={{ fontSize: 11, opacity: 0.6 }}>
                Changement possible dans {Math.ceil(faction.changeCooldownRemainingMs / 1000)}s
              </p>
            )}
            <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
              {faction.definitions.map((d) => {
                const count = faction.states.find((s) => s.id === d.id)?.memberCount ?? 0;
                const mine = faction.myFactionId === d.id;
                return (
                  <li key={d.id} style={{ display: "flex", alignItems: "center", gap: 8, padding: "4px 0" }}>
                    <span
                      style={{ width: 12, height: 12, background: d.color, display: "inline-block", borderRadius: 2 }}
                    />
                    <span style={{ flex: 1 }}>
                      {d.name} <span style={{ opacity: 0.5 }}>({count})</span>
                    </span>
                    {mine ? (
                      <Button size="sm" variant="danger" onClick={() => emit("faction_set:")}>
                        Quitter
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        disabled={faction.changeCooldownRemainingMs > 0}
                        onClick={() => emit(`faction_set:${d.id}`)}
                      >
                        Rejoindre
                      </Button>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
