import { AttackMeta, SpellMeta } from "../types";
import { UiState } from "../UIReducer";
import { useCooldownDisplay } from "../hooks/useCooldownDisplay";
import { hasEnoughResources } from "./AttackPanel";

export function AttackCooldownOverlay({
  id,
  meta,
  playerStatus,
}: {
  id: string;
  meta: AttackMeta | SpellMeta | null;
  playerStatus: UiState["playerStatus"] | undefined;
}) {
  const serverCd = playerStatus?.attackCooldownsRemainingMs?.[id] ?? 0;
  const cooldownDisplay = useCooldownDisplay(serverCd);
  const hasCd = cooldownDisplay > 0;
  const hasRes = meta ? hasEnoughResources(meta, playerStatus) : true;

  return (
    <>
      {hasCd ? (
        <div className="absolute bottom-0.5 right-0.5 text-white font-mono text-[8px] leading-none [text-shadow:1px_1px_0_#000]">
          {(cooldownDisplay / 1000).toFixed(1)}
        </div>
      ) : !hasRes ? (
        <div className="absolute bottom-0 right-0 text-[10px] leading-none select-none">🚫</div>
      ) : null}
    </>
  );
}
