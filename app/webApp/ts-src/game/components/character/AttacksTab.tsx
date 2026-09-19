import { useState } from "react";
import { AttackMeta, SpellMeta } from "../../types";
import { damageTypeColor } from "../AttackPanel";
import { useAttackDrag } from "../../hooks/useAttackDrag";
import { AttackTooltip } from "../../shared/AttackTooltip";
import { SpellTooltip } from "../../shared/SpellTooltip";

export function AttacksTab({
  attackMeta,
  spellMeta,
}: {
  attackMeta: Record<string, AttackMeta>;
  spellMeta: Record<string, SpellMeta>;
}) {
  const attacks = Object.entries(attackMeta);
  const spells = Object.entries(spellMeta);
  const [hoveredKey, setHoveredKey] = useState<string | null>(null);
  const { startDrag, moveDrag, endDrag, guardClick } = useAttackDrag(
    (id) => damageTypeColor(attackMeta[id]?.damageType ?? ""),
    "attack",
  );
  const {
    startDrag: startSpellDrag,
    moveDrag: moveSpellDrag,
    endDrag: endSpellDrag,
    guardClick: guardSpellClick,
  } = useAttackDrag(() => "#ea580c", "spell");

  if (attacks.length === 0 && spells.length === 0) {
    return <div className="text-white/30 text-xs">Aucune attaque disponible.</div>;
  }

  return (
    <div className="space-y-4">
      <div className="text-white/40 text-[10px] font-mono mb-1">
        Alt+glisser pour placer dans la barre de raccourcis
      </div>
      {attacks.length > 0 && (
        <div>
          <div className="text-blue-300 text-xs font-mono mb-3 tracking-widest">ATTAQUES</div>
          <div className="flex flex-wrap gap-2">
            {attacks.map(([id, meta]) => {
              const displayName = meta.attackId ?? id;
              return (
                <div
                  key={id}
                  onPointerDown={(e) => startDrag(e, id)}
                  onPointerMove={moveDrag}
                  onPointerUp={endDrag}
                  onPointerCancel={endDrag}
                  onClick={() => guardClick(() => {})}
                  onPointerEnter={() => setHoveredKey(`attack-${id}`)}
                  onPointerLeave={() => setHoveredKey(null)}
                  onMouseDown={(e) => e.stopPropagation()}
                  className="w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-white/25 bg-black/72 cursor-grab hover:border-white/60 transition-colors touch-none select-none"
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
                  {hoveredKey === `attack-${id}` && <AttackTooltip id={displayName} meta={meta} />}
                </div>
              );
            })}
          </div>
        </div>
      )}
      {spells.length > 0 && (
        <div>
          <div className="text-blue-300 text-xs font-mono mb-3 tracking-widest">SORTS</div>
          <div className="flex flex-wrap gap-2">
            {spells.map(([id, meta]) => (
              <div
                key={id}
                onPointerDown={(e) => startSpellDrag(e, id)}
                onPointerMove={moveSpellDrag}
                onPointerUp={endSpellDrag}
                onPointerCancel={endSpellDrag}
                onClick={() => guardSpellClick(() => {})}
                onPointerEnter={() => setHoveredKey(`spell-${id}`)}
                onPointerLeave={() => setHoveredKey(null)}
                onMouseDown={(e) => e.stopPropagation()}
                className="w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-orange-400/60 bg-black/72 cursor-grab hover:border-orange-400 transition-colors touch-none select-none"
              >
                <div className="text-orange-400 text-lg leading-none">⚡</div>
                <div className="text-orange-300/80 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                  {id}
                </div>
                {hoveredKey === `spell-${id}` && <SpellTooltip id={id} meta={meta} />}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
