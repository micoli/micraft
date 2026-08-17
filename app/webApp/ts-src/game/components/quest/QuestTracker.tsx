import React, { useState, useEffect } from "react";
import { getApiQuests } from "../../../generated/api/requests";
import { QuestProgress } from "../../types";

type KillObjective = { npcType: string; requiredCount: number };
type QuestDef = {
  id: string;
  title: string;
  type: string;
  objectives: KillObjective[];
  itemType: string | null;
  requiredCount: number;
};

interface Props {
  visible: boolean;
  quests: Record<string, QuestProgress>;
  layoutStyle: React.CSSProperties;
}

export function QuestTracker({ visible, quests, layoutStyle }: Props) {
  const [definitions, setDefinitions] = useState<Record<string, QuestDef>>({});

  useEffect(() => {
    getApiQuests({ throwOnError: true })
      .then((r) => {
        const map: Record<string, QuestDef> = {};
        for (const q of r.data as unknown as QuestDef[]) map[q.id] = q;
        setDefinitions(map);
      })
      .catch(() => {});
  }, []);

  if (!visible) return null;

  const active = Object.entries(quests).filter(([, p]) => p.status === "IN_PROGRESS");
  if (active.length === 0) return null;

  return (
    <div style={{ ...layoutStyle, zIndex: 200, overflowY: "auto", pointerEvents: "none" }}>
      {active.map(([id, progress]) => {
        const def = definitions[id];
        if (!def) return null;

        const objectives =
          def.type === "KILL" || def.type === "BOSS"
            ? def.objectives.map((obj) => ({
                label: obj.npcType,
                current: (progress.progress ?? {})[obj.npcType] ?? 0,
                required: obj.requiredCount,
              }))
            : def.type === "FETCH" && def.itemType
              ? [
                  {
                    label: def.itemType,
                    current: (progress.progress ?? {})[def.itemType] ?? 0,
                    required: def.requiredCount,
                  },
                ]
              : [];

        const leastComplete =
          objectives.length > 0
            ? objectives.reduce((a, b) => (a.current / a.required < b.current / b.required ? a : b))
            : null;
        const displayObj = leastComplete ?? (objectives.length > 0 ? objectives[0] : null);

        return (
          <div key={id} className="mb-2 rounded bg-black/70 border border-white/10 px-2 py-1.5 backdrop-blur-sm">
            <p className="text-xs font-semibold text-yellow-400 leading-tight mb-0.5 truncate">{def.title}</p>
            {displayObj && (
              <>
                <div className="flex justify-between text-[10px] text-white/60">
                  <span className="truncate mr-1">{displayObj.label}</span>
                  <span className="shrink-0">
                    {displayObj.current}/{displayObj.required}
                  </span>
                </div>
                <div className="h-1 bg-white/10 rounded-full mt-0.5 overflow-hidden">
                  <div
                    className="h-full bg-yellow-500 rounded-full"
                    style={{ width: `${Math.min(100, (displayObj.current / displayObj.required) * 100)}%` }}
                  />
                </div>
                {objectives.length > 1 && (
                  <p className="text-[10px] text-white/30 mt-0.5">+{objectives.length - 1} more</p>
                )}
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}
