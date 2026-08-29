import { useEffect, useState } from "react";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Field } from "../../../primitives/Field";
import { NumberInput } from "../../../primitives/NumberInput";
import { Input } from "../../../primitives/Input";
import { Button } from "../../../primitives/Button";
import { RemovableBadge } from "../../../primitives/RemovableBadge";
import { ClaimDto } from "../../apiTypes";

interface Props {
  claim: ClaimDto;
  onClose: () => void;
  onSaveBounds: (yMin: number, yMax: number) => Promise<void>;
  onSetTrusted: (playerName: string, trusted: boolean) => Promise<void>;
  onDelete: () => Promise<void>;
}

export function ClaimEditDialog({ claim, onClose, onSaveBounds, onSetTrusted, onDelete }: Props) {
  const [yMin, setYMin] = useState(claim.yMin);
  const [yMax, setYMax] = useState(claim.yMax);
  const [trustName, setTrustName] = useState("");
  const [savingBounds, setSavingBounds] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setYMin(claim.yMin);
    setYMax(claim.yMax);
  }, [claim]);

  const handleSaveBounds = async () => {
    setSavingBounds(true);
    setError(null);
    try {
      await onSaveBounds(yMin, yMax);
    } catch (e) {
      setError(String(e));
    } finally {
      setSavingBounds(false);
    }
  };

  const handleTrust = async () => {
    const name = trustName.trim();
    if (!name) return;
    setError(null);
    try {
      await onSetTrusted(name, true);
      setTrustName("");
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <Dialog open onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="w-[480px] max-w-[95vw]">
        <DialogTitle>Edit Claim</DialogTitle>
        <div className="space-y-4 mt-3">
          <div className="text-sm text-[#8A99AF]">
            Owner: <span className="text-white">{claim.ownerName}</span> · {claim.chunks.length} chunk
            {claim.chunks.length > 1 ? "s" : ""}
          </div>

          <div className="flex gap-3 items-end">
            <Field label="Y min">
              <NumberInput value={yMin} onChange={(e) => setYMin(parseInt(e.target.value) || 0)} />
            </Field>
            <Field label="Y max">
              <NumberInput value={yMax} onChange={(e) => setYMax(parseInt(e.target.value) || 0)} />
            </Field>
            <Button size="sm" variant="secondary" disabled={savingBounds} onClick={handleSaveBounds}>
              Save bounds
            </Button>
          </div>

          {error && <p className="text-red-400 text-xs">{error}</p>}

          <Field label="Trusted players">
            <div className="flex flex-wrap gap-1.5 mb-2">
              {(claim.trustedPlayerNames ?? []).length === 0 && <span className="text-xs text-[#8A99AF]">none</span>}
              {(claim.trustedPlayerNames ?? []).map((name) => (
                <RemovableBadge key={name} name={name} onRemove={() => onSetTrusted(name, false)} />
              ))}
            </div>
            <div className="flex gap-2">
              <Input
                placeholder="Player name"
                value={trustName}
                onChange={(e) => setTrustName(e.target.value)}
                style={{ flex: 1 }}
              />
              <Button size="sm" variant="secondary" disabled={!trustName.trim()} onClick={handleTrust}>
                Trust
              </Button>
            </div>
          </Field>

          <div className="flex justify-between pt-2">
            <Button variant="danger" size="sm" onClick={onDelete}>
              Delete Claim
            </Button>
            <Button variant="ghost" onClick={onClose}>
              Close
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
