import { useState } from "react";
import { Button } from "../../../primitives/Button";
import { Input } from "../../../primitives/Input";
import { RemovableBadge } from "../../../primitives/RemovableBadge";
import { ClaimData } from "../../types";

interface Props {
  claim: ClaimData;
  isOwner: boolean;
  playerNames: string[];
  onAbandon: (claimId: string) => void;
  onSetTrusted: (claimId: string, playerName: string, trusted: boolean) => void;
}

export function ClaimRow({ claim, isOwner, playerNames, onAbandon, onSetTrusted }: Props) {
  const [trustName, setTrustName] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [suggestionIdx, setSuggestionIdx] = useState(-1);

  function updateSuggestions(val: string) {
    setSuggestionIdx(-1);
    if (val.length >= 1) {
      const filtered = playerNames.filter((n) => n.toLowerCase().includes(val.toLowerCase()));
      setSuggestions(filtered.slice(0, 8));
      setShowSuggestions(filtered.length > 0);
    } else {
      setShowSuggestions(false);
    }
  }

  function pickSuggestion(name: string) {
    setTrustName(name);
    setShowSuggestions(false);
    setSuggestionIdx(-1);
  }

  function handleTrustNameKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (!showSuggestions) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSuggestionIdx((i) => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSuggestionIdx((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter" || e.key === "Tab") {
      const pick = suggestionIdx >= 0 ? suggestions[suggestionIdx] : suggestions[0];
      if (pick) {
        e.preventDefault();
        pickSuggestion(pick);
      }
    } else if (e.key === "Escape") {
      setShowSuggestions(false);
    }
  }

  return (
    <div
      style={{
        border: "1px solid rgba(255,255,255,0.15)",
        borderRadius: 6,
        padding: 12,
        display: "flex",
        flexDirection: "column",
        gap: 6,
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between" }}>
        <strong>{claim.ownerName}</strong>
        <span style={{ opacity: 0.6, fontSize: 12 }}>
          {claim.chunks.length} chunk{claim.chunks.length > 1 ? "s" : ""} · y {claim.yMin}-{claim.yMax}
        </span>
      </div>
      {!isOwner && (
        <div style={{ fontSize: 12, opacity: 0.8 }}>
          Trusted: {claim.trustedPlayerNames.length > 0 ? claim.trustedPlayerNames.join(", ") : "none"}
        </div>
      )}
      {isOwner && (
        <>
          <div style={{ display: "flex", gap: 6 }}>
            <div className="relative" style={{ flex: 1 }}>
              <Input
                placeholder="Player name"
                value={trustName}
                onChange={(e) => {
                  setTrustName(e.target.value);
                  updateSuggestions(e.target.value);
                }}
                onKeyDown={handleTrustNameKeyDown}
                onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
                onFocus={() => trustName.length >= 1 && suggestions.length > 0 && setShowSuggestions(true)}
                autoComplete="off"
                style={{ width: "100%" }}
              />
              {showSuggestions && (
                <div className="absolute top-full left-0 right-0 z-10 bg-black/90 border border-white/20 rounded mt-1 max-h-40 overflow-y-auto">
                  {suggestions.map((name, i) => (
                    <div
                      key={name}
                      className={`px-3 py-1.5 text-sm font-mono cursor-pointer text-white/80 transition-colors ${i === suggestionIdx ? "bg-white/20" : "hover:bg-white/10"}`}
                      onMouseDown={() => pickSuggestion(name)}
                      onMouseEnter={() => setSuggestionIdx(i)}
                    >
                      {name}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <Button
              size="sm"
              variant="secondary"
              disabled={!trustName.trim()}
              onClick={() => {
                onSetTrusted(claim.id, trustName.trim(), true);
                setTrustName("");
              }}
            >
              Trust
            </Button>
          </div>
          {claim.trustedPlayerNames.length > 0 && (
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {claim.trustedPlayerNames.map((name) => (
                <RemovableBadge key={name} name={name} onRemove={() => onSetTrusted(claim.id, name, false)} />
              ))}
            </div>
          )}
          <Button size="sm" variant="outline" onClick={() => onAbandon(claim.id)}>
            Abandon Claim
          </Button>
        </>
      )}
    </div>
  );
}
