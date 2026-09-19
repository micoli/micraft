import { useState } from "react";
import { cn } from "../../primitives/cn";
import { AttackMeta, SpellMeta } from "../types";
import { UiState } from "../UIReducer";
import { useAttackDrag } from "../hooks/useAttackDrag";
import { AttackCooldownOverlay } from "../shared/AttackCooldownOverlay";
import { AttackTooltip } from "../shared/AttackTooltip";
import { SpellTooltip } from "../shared/SpellTooltip";
import { MacroTooltip } from "../shared/MacroTooltip";

export function damageTypeColor(damageType: string): string {
  switch (damageType) {
    case "FIRE":
      return "#e05a00";
    case "POISON":
      return "#4a9e2f";
    case "MAGIC":
      return "#7b2fe0";
    case "LIGHTNING":
      return "#c8d400";
    case "NECROTIC":
      return "#3d1a5e";
    case "PHYSICAL":
    default:
      return "#8a6a3a";
  }
}

export function spellTypeColor(type: string): string {
  switch (type) {
    case "DAMAGE":
      return "#7b2fe0";
    case "HEAL":
      return "#2fae4a";
    case "BUFF":
      return "#d4af37";
    default:
      return "#ea580c";
  }
}

export function hasEnoughResources(meta: AttackMeta | SpellMeta, status: UiState["playerStatus"] | undefined): boolean {
  if (!status) return true;
  if (meta.manaCost > 0 && status.currentMana < meta.manaCost) return false;
  if (meta.rageCost > 0 && status.currentRage < meta.rageCost) return false;
  if ("tokenCost" in meta && meta.tokenCost > 0 && (status.currentTokens ?? 0) < meta.tokenCost) return false;
  return true;
}

interface Props {
  attackMeta: Record<string, AttackMeta>;
  spellMeta?: Record<string, SpellMeta>;
  /** Spell ids the player's class has unlocked at their current level. Undefined = unknown yet (nothing greyed out). */
  unlockedSpellIds?: Set<string>;
  layoutStyle?: React.CSSProperties;
  pinnedMacros?: string[];
  playerStatus?: UiState["playerStatus"];
}

export function AttackPanel({
  attackMeta,
  spellMeta = {},
  unlockedSpellIds,
  layoutStyle,
  pinnedMacros = [],
  playerStatus,
}: Props) {
  const attacks = Object.entries(attackMeta);
  const spells = Object.entries(spellMeta);
  const spellsByRank = new Map<number, [string, SpellMeta][]>();
  for (const entry of spells) {
    const rank = entry[1].rank;
    const floor = spellsByRank.get(rank);
    if (floor) floor.push(entry);
    else spellsByRank.set(rank, [entry]);
  }
  const spellRanks = [...spellsByRank.keys()].sort((a, b) => a - b);
  const [hoveredKey, setHoveredKey] = useState<string | null>(null);
  const { startDrag, moveDrag, endDrag, guardClick } = useAttackDrag((id) =>
    damageTypeColor(attackMeta[id]?.damageType ?? ""),
  );
  const {
    startDrag: startMacroDrag,
    moveDrag: moveMacroDrag,
    endDrag: endMacroDrag,
    guardClick: guardMacroClick,
  } = useAttackDrag(() => "#b45309", "macro");
  const {
    startDrag: startSpellDrag,
    moveDrag: moveSpellDrag,
    endDrag: endSpellDrag,
    guardClick: guardSpellClick,
  } = useAttackDrag(() => "#ea580c", "spell");
  if (attacks.length === 0 && spells.length === 0 && pinnedMacros.length === 0) return null;

  return (
    <div
      className={cn(
        "pointer-events-auto z-[999] bg-black/60 border border-white/20 rounded-md p-2",
        !layoutStyle && "fixed bottom-24 left-1/2 -translate-x-1/2",
      )}
      style={layoutStyle}
    >
      <div className="flex flex-wrap gap-1 content-start">
        {pinnedMacros.map((name) => (
          <div
            key={`macro-${name}`}
            onClick={() => guardMacroClick(() => window.mcRunMacro?.(name))}
            onPointerDown={(e) => startMacroDrag(e, name)}
            onPointerMove={moveMacroDrag}
            onPointerUp={endMacroDrag}
            onPointerCancel={endMacroDrag}
            onPointerEnter={() => setHoveredKey(`macro-${name}`)}
            onPointerLeave={() => setHoveredKey(null)}
            className="w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-amber-400/40 bg-black/72 cursor-grab hover:border-amber-400/80 transition-colors touch-none"
          >
            <div className="text-amber-400/80 font-mono text-base">⚡</div>
            <div className="text-amber-300/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
              {name}
            </div>
            {hoveredKey === `macro-${name}` && <MacroTooltip id={name} />}
          </div>
        ))}
        {attacks.map(([id, meta]) => {
          const hasCd = (playerStatus?.attackCooldownsRemainingMs?.[id] ?? 0) > 0;
          const displayName = meta.attackId ?? id;
          return (
            <div
              key={`attack-${id}`}
              onClick={() => guardClick(() => window.mcState?.events?.push(`attack:${id}`))}
              onPointerDown={(e) => startDrag(e, id)}
              onPointerMove={moveDrag}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
              onPointerEnter={() => setHoveredKey(`attack-${id}`)}
              onPointerLeave={() => setHoveredKey(null)}
              className={cn(
                "w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-white/25 bg-black/72 cursor-grab hover:border-white/60 transition-colors touch-none",
                hasCd && "opacity-50",
              )}
            >
              <div
                className="w-[26px] h-[26px] rounded-full"
                style={{
                  background: damageTypeColor(meta.damageType),
                  boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.2)",
                }}
              />
              <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                {displayName}
              </div>
              {meta.rank > 1 && (
                <div className="absolute top-0.5 left-1 text-yellow-300 font-mono font-bold text-[8px]">
                  {meta.rank}
                </div>
              )}
              {meta.manaCost > 0 && (
                <div className="absolute top-0.5 right-1 text-blue-300 font-mono font-bold text-[8px]">
                  {meta.manaCost}
                </div>
              )}
              <AttackCooldownOverlay id={id} meta={meta} playerStatus={playerStatus} />
              {hoveredKey === `attack-${id}` && <AttackTooltip id={displayName} meta={meta} />}
            </div>
          );
        })}
      </div>
      {spellRanks.length > 0 && (
        <div className="relative flex flex-col-reverse items-center gap-3 mt-2">
          <div className="absolute top-0 bottom-0 w-px bg-orange-400/25" />
          {spellRanks.map((rank) => (
            <div key={`floor-${rank}`} className="relative flex flex-col items-center gap-1">
              <div className="text-orange-300/50 font-mono text-[8px] tracking-[0.5px]">Niveau {rank}</div>
              <div className="flex flex-wrap justify-center gap-1">
                {spellsByRank.get(rank)!.map(([id, meta]) => {
                  const hasRes = hasEnoughResources(meta, playerStatus);
                  const isLocked = unlockedSpellIds !== undefined && !unlockedSpellIds.has(id);
                  return (
                    <div
                      key={`spell-${id}`}
                      onClick={() => guardSpellClick(() => window.mcState?.events?.push(`spell:${id}`))}
                      onPointerDown={(e) => startSpellDrag(e, id)}
                      onPointerMove={moveSpellDrag}
                      onPointerUp={endSpellDrag}
                      onPointerCancel={endSpellDrag}
                      onPointerEnter={() => setHoveredKey(`spell-${id}`)}
                      onPointerLeave={() => setHoveredKey(null)}
                      className={cn(
                        "w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-orange-400/60 bg-black/72 cursor-grab hover:border-orange-400 transition-colors touch-none",
                        (!hasRes || isLocked) && "opacity-50",
                        isLocked && "grayscale",
                      )}
                    >
                      <div
                        className="w-[26px] h-[26px] rounded-full"
                        style={{
                          background: spellTypeColor(meta.type),
                          boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.2)",
                        }}
                      />
                      <div className="text-orange-300/80 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                        {id}
                      </div>
                      {isLocked ? (
                        <div className="absolute top-0.5 left-1 text-white/70 font-mono font-bold text-[9px]">🔒</div>
                      ) : (
                        meta.rank > 1 && (
                          <div className="absolute top-0.5 left-1 text-yellow-300 font-mono font-bold text-[8px]">
                            {meta.rank}
                          </div>
                        )
                      )}
                      {meta.manaCost > 0 && (
                        <div className="absolute top-0.5 right-1 text-blue-300 font-mono font-bold text-[8px]">
                          {meta.manaCost}
                        </div>
                      )}
                      {meta.tokenCost > 0 && (
                        <div className="absolute bottom-0.5 right-1 text-orange-300 font-mono font-bold text-[8px]">
                          {meta.tokenCost}t
                        </div>
                      )}
                      <AttackCooldownOverlay id={id} meta={meta} playerStatus={playerStatus} />
                      {hoveredKey === `spell-${id}` && <SpellTooltip id={id} meta={meta} isLocked={isLocked} />}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
