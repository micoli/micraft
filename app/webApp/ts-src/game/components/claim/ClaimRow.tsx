import { useState } from "react";
import { Button } from "../../../primitives/Button";
import { Input } from "../../../primitives/Input";
import { ClaimData } from "../../types";

interface Props {
  claim: ClaimData;
  isOwner: boolean;
  onAbandon: (claimId: string) => void;
  onSetTrusted: (claimId: string, playerName: string, trusted: boolean) => void;
}

export function ClaimRow({ claim, isOwner, onAbandon, onSetTrusted }: Props) {
  const [trustName, setTrustName] = useState("");

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
      <div style={{ fontSize: 12, opacity: 0.8 }}>
        Trusted: {claim.trustedPlayerNames.length > 0 ? claim.trustedPlayerNames.join(", ") : "none"}
      </div>
      {isOwner && (
        <>
          <div style={{ display: "flex", gap: 6 }}>
            <Input
              placeholder="Player name"
              value={trustName}
              onChange={(e) => setTrustName(e.target.value)}
              style={{ flex: 1 }}
            />
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
                <Button key={name} size="sm" variant="outline" onClick={() => onSetTrusted(claim.id, name, false)}>
                  Untrust {name}
                </Button>
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
