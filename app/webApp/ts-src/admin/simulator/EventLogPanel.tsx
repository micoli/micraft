import { memo, useEffect, useMemo, useRef, useState } from "react";
import {
  ALL_NPC_TYPES,
  EVENT_COLORS,
  EVENT_LABELS,
  filterEvents,
  npcTypesInEvents,
  type SimEvent,
  type SimEventType,
} from "./types";

const FILTER_GROUPS: { label: string; types: SimEventType[] }[] = [
  { label: "Combat", types: ["ATTACK", "DAMAGE", "DEATH", "AGGRO_GAIN", "AGGRO_LOST"] },
  { label: "Faim", types: ["HUNGRY", "FED"] },
  { label: "Reproduction", types: ["MATING", "GESTATION_START", "BIRTH", "EVOLVE"] },
  { label: "Cycle de vie", types: ["SPAWN", "DESPAWN", "AGE_DEATH"] },
  { label: "Système", types: ["SYSTEM"] },
];

interface Props {
  events: SimEvent[];
  onSelect: (npcId: string) => void;
}

/**
 * Rolling 300-line history of what the arena did, filterable by family of event. Memoized: the parent
 * re-renders on every frame, this only needs to when the events actually change.
 */
export const EventLogPanel = memo(function EventLogPanel({ events, onSelect }: Props) {
  const [enabled, setEnabled] = useState<Record<string, boolean>>(() =>
    FILTER_GROUPS.reduce((acc, g) => ({ ...acc, [g.label]: true }), {}),
  );
  const [npcType, setNpcType] = useState<string>(ALL_NPC_TYPES);
  const [autoScroll, setAutoScroll] = useState(true);
  const listRef = useRef<HTMLDivElement | null>(null);

  const activeTypes = useMemo(() => {
    const set = new Set<SimEventType>();
    FILTER_GROUPS.forEach((g) => {
      if (enabled[g.label]) g.types.forEach((t) => set.add(t));
    });
    return set;
  }, [enabled]);

  const npcTypes = useMemo(() => npcTypesInEvents(events), [events]);
  // newest first: the line you care about is the one at the top
  const visible = useMemo(() => filterEvents(events, activeTypes, npcType, true), [events, activeTypes, npcType]);
  const newestSeq = visible[0]?.seq ?? 0;

  // Keyed on the newest row, not on the row count: the history caps at 300, so the count stops
  // changing and an effect watching it would stop scrolling exactly when the log gets busy.
  useEffect(() => {
    if (autoScroll && listRef.current) listRef.current.scrollTop = 0;
  }, [newestSeq, autoScroll]);

  return (
    <div className="flex h-full flex-col">
      <div className="mb-2 flex flex-wrap items-center gap-1.5">
        {FILTER_GROUPS.map((group) => (
          <button
            key={group.label}
            type="button"
            onClick={() => setEnabled((s) => ({ ...s, [group.label]: !s[group.label] }))}
            className={
              "rounded px-2 py-0.5 text-[10px] font-medium transition-colors " +
              (enabled[group.label] ? "bg-[#3C50E0] text-white" : "bg-[#2E3A4E] text-[#8A99AF] hover:text-white")
            }
          >
            {group.label}
          </button>
        ))}
        <select
          value={npcType}
          onChange={(e) => setNpcType(e.target.value)}
          title="Filtrer par type de NPC"
          className="rounded border border-[#2E3A4E] bg-[#0E1726] px-1.5 py-0.5 text-[10px] text-white"
        >
          <option value={ALL_NPC_TYPES}>tous les types</option>
          {npcTypes.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
        <label className="ml-auto flex items-center gap-1.5 text-[10px] text-[#8A99AF]">
          <input
            type="checkbox"
            checked={autoScroll}
            onChange={(e) => setAutoScroll(e.target.checked)}
            className="accent-[#3C50E0]"
          />
          suivre les derniers
        </label>
      </div>

      <div
        ref={listRef}
        className="flex-1 overflow-auto rounded border border-[#2E3A4E] bg-[#0E1726] p-1.5 font-mono text-[11px] leading-relaxed"
      >
        {visible.length === 0 && <p className="p-2 text-[#8A99AF]">Aucun évènement — démarre la simulation.</p>}
        {visible.map((event) => (
          <div
            key={event.seq}
            onClick={() => event.npcId && onSelect(event.npcId)}
            className={"flex gap-2 rounded px-1 py-0.5 " + (event.npcId ? "cursor-pointer hover:bg-[#1A222C]" : "")}
          >
            <span className="w-24 shrink-0 text-[#4A5568]">
              t{event.tick} j{event.gameDay.toFixed(2)}
            </span>
            <span className="w-24 shrink-0" style={{ color: EVENT_COLORS[event.type] }}>
              {EVENT_LABELS[event.type]}
            </span>
            <span className="text-[#CBD5E1]">{event.message}</span>
          </div>
        ))}
      </div>

      <p className="mt-1.5 text-[10px] text-[#8A99AF]">
        {visible.length} ligne(s) affichée(s) · historique conservé : {events.length}/300 · rafraîchi toutes les 500 ms
      </p>
    </div>
  );
});
