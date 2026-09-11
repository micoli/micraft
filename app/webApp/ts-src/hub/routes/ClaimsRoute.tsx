import { useState } from "react";
import { ClaimPanel } from "../../game/components/claim/ClaimPanel";
import type { ClaimData } from "../../game/types";
import { useHubMessage, useHubSend, useHubSocket } from "../state/HubSocketProvider";
import type { ClaimDeniedMsg, ClaimSyncMsg } from "../lib/hubCodec";

/**
 * Reuses the exact in-game `ClaimPanel`/`ClaimRow` (trustee list + autocomplete, abandon) — pure
 * callback props already, wired to the hub socket instead of the wasm bridge. Creating a new claim
 * needs a 3D view to pick two block corners, so it stays game-only (`ClaimCreate` isn't in the
 * hub's message subset — see HubConnection.kt) — this screen only manages existing claims.
 */
export function ClaimsRoute() {
  const [claims, setClaims] = useState<ClaimData[]>([]);
  const [denied, setDenied] = useState<string | null>(null);
  const { playerId } = useHubSocket();
  const send = useHubSend();

  useHubMessage<ClaimSyncMsg>("ClaimSync", (p) => setClaims(p.claims));
  useHubMessage<ClaimDeniedMsg>("ClaimDenied", (p) => setDenied(p.reason));

  return (
    <>
      {denied && (
        <div className="bg-red-950/60 border-b border-red-800/60 text-red-300 text-xs px-4 py-2 flex items-center justify-between">
          <span>{denied}</span>
          <button className="text-red-300/70 hover:text-red-300" onClick={() => setDenied(null)}>
            ✕
          </button>
        </div>
      )}
      <ClaimPanel
        open
        claims={claims}
        myPlayerId={playerId}
        onClose={() => {}}
        chrome="page"
        onAbandon={(claimId) => send("ClaimAbandon", { claimId })}
        onSetTrusted={(claimId, playerName, trusted) => send("ClaimSetTrusted", { claimId, playerName, trusted })}
      />
    </>
  );
}
