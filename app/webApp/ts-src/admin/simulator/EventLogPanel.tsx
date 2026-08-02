import { memo, useEffect, useMemo, useRef, useState } from "react";
import { useT, type TranslationKey } from "../i18n";
import {
  ALL_NPC_TYPES,
  EVENT_COLORS,
  EVENT_LABEL_KEYS,
  EVENT_HISTORY,
  filterEvents,
  npcTypesInEvents,
  type SimEvent,
  type SimEventType,
} from "./types";

// Grouped by key, not by label: the enabled-set is persisted in component state across a locale
// change, and keying it on rendered text would reset every filter when the language switches.
const FILTER_GROUPS: { key: string; labelKey: TranslationKey; types: SimEventType[] }[] = [
  {
    key: "combat",
    labelKey: "sim.eventGroup.combat",
    types: ["ATTACK", "DAMAGE", "DEATH", "AGGRO_GAIN", "AGGRO_LOST"],
  },
  { key: "hunger", labelKey: "sim.eventGroup.hunger", types: ["HUNGRY", "FED"] },
  {
    key: "reproduction",
    labelKey: "sim.eventGroup.reproduction",
    types: ["MATING", "GESTATION_START", "BIRTH", "EVOLVE"],
  },
  { key: "lifecycle", labelKey: "sim.eventGroup.lifecycle", types: ["SPAWN", "DESPAWN", "AGE_DEATH"] },
  { key: "system", labelKey: "sim.eventGroup.system", types: ["SYSTEM"] },
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
  const t = useT();
  const [enabled, setEnabled] = useState<Record<string, boolean>>(() =>
    FILTER_GROUPS.reduce((acc, g) => ({ ...acc, [g.key]: true }), {}),
  );
  const [npcType, setNpcType] = useState<string>(ALL_NPC_TYPES);
  const [autoScroll, setAutoScroll] = useState(true);
  const listRef = useRef<HTMLDivElement | null>(null);

  const activeTypes = useMemo(() => {
    const set = new Set<SimEventType>();
    FILTER_GROUPS.forEach((g) => {
      if (enabled[g.key]) g.types.forEach((type) => set.add(type));
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
            key={group.key}
            type="button"
            onClick={() => setEnabled((s) => ({ ...s, [group.key]: !s[group.key] }))}
            className={
              "rounded px-2 py-0.5 text-[10px] font-medium transition-colors " +
              (enabled[group.key] ? "bg-[#3C50E0] text-white" : "bg-[#2E3A4E] text-[#8A99AF] hover:text-white")
            }
          >
            {t(group.labelKey)}
          </button>
        ))}
        <select
          value={npcType}
          onChange={(e) => setNpcType(e.target.value)}
          title={t("sim.eventLog.filterByType")}
          className="rounded border border-[#2E3A4E] bg-[#0E1726] px-1.5 py-0.5 text-[10px] text-white"
        >
          <option value={ALL_NPC_TYPES}>{t("sim.eventLog.allTypes")}</option>
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
          {t("sim.eventLog.followLatest")}
        </label>
      </div>

      <div
        ref={listRef}
        className="flex-1 overflow-auto rounded border border-[#2E3A4E] bg-[#0E1726] p-1.5 font-mono text-[11px] leading-relaxed"
      >
        {visible.length === 0 && <p className="p-2 text-[#8A99AF]">{t("sim.eventLog.empty")}</p>}
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
              {t(EVENT_LABEL_KEYS[event.type])}
            </span>
            <span className="text-[#CBD5E1]">{event.message}</span>
          </div>
        ))}
      </div>

      <p className="mt-1.5 text-[10px] text-[#8A99AF]">
        {t("sim.eventLog.footer", visible.length, events.length, EVENT_HISTORY)}
      </p>
    </div>
  );
});
