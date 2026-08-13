import { useState, useMemo } from "react";
import { useForm, useStore } from "@tanstack/react-form";
import { Button } from "../../primitives/Button";
import { cn } from "../../primitives/cn";

const POINT_BUY_COST: Record<number, number> = {
  8: 0,
  9: 1,
  10: 2,
  11: 3,
  12: 4,
  13: 5,
  14: 7,
  15: 9,
};
const BUDGET = 27;
const STAT_MIN = 8;
const STAT_MAX = 15;

type StatKey = "str" | "dex" | "intel" | "wis" | "con" | "cha";

interface ClassDef {
  label: string;
  description: string;
  bonuses: Partial<Record<StatKey, number>>;
}

const CLASSES: Record<string, ClassDef> = {
  WARRIOR: { label: "Warrior", description: "Melee fighter. High STR and CON.", bonuses: { str: 2, con: 1 } },
  MAGE: { label: "Mage", description: "Spellcaster. High INT and WIS.", bonuses: { intel: 2, wis: 1 } },
  RANGER: { label: "Ranger", description: "Archer. High DEX and WIS.", bonuses: { dex: 2, wis: 1 } },
  ROGUE: { label: "Rogue", description: "Quick striker. High DEX and INT.", bonuses: { dex: 2, intel: 1 } },
  CLERIC: { label: "Cleric", description: "Healer. High WIS and CON.", bonuses: { wis: 2, con: 1 } },
};

const STAT_LABELS: { key: StatKey; label: string; abbrev: string }[] = [
  { key: "str", label: "Strength", abbrev: "STR" },
  { key: "dex", label: "Dexterity", abbrev: "DEX" },
  { key: "intel", label: "Intelligence", abbrev: "INT" },
  { key: "wis", label: "Wisdom", abbrev: "WIS" },
  { key: "con", label: "Constitution", abbrev: "CON" },
  { key: "cha", label: "Charisma", abbrev: "CHA" },
];

type BaseStats = Record<StatKey, number>;

function computeDerived(stats: BaseStats, level: number) {
  const floor = (n: number) => Math.floor(n);
  return {
    maxHp: Math.max(1, floor((stats.con - 10) / 2) * level + 10),
    maxMana: stats.wis * 5,
    meleeDmg: floor((stats.str - 10) / 2),
    rangedDmg: floor((stats.dex - 10) / 2),
    spellDmg: floor((stats.intel - 10) / 2),
    critChance: (5 + stats.dex * 0.2).toFixed(1),
    dodgePct: Math.min(75, stats.dex * 2.5).toFixed(1),
    magicResist: Math.max(0, (stats.wis - 10) * 2).toFixed(0),
    initiative: floor((stats.dex - 10) / 2),
  };
}

interface FormProps {
  required: boolean;
  onSubmit: (cmd: string) => void;
  onCancel?: () => void;
}

export function CharacterCreationForm({ required, onSubmit, onCancel }: FormProps) {
  const [error, setError] = useState("");

  const form = useForm({
    defaultValues: {
      name: "",
      selectedClass: "WARRIOR",
      allocation: { str: 8, dex: 8, intel: 8, wis: 8, con: 8, cha: 8 } as BaseStats,
    },
    onSubmit: ({ value }) => {
      const { str, dex, intel, wis, con, cha } = value.allocation;
      onSubmit(
        `/createcharacter ${value.name.trim()} ${value.selectedClass} ${str} ${dex} ${intel} ${wis} ${con} ${cha}`,
      );
    },
  });

  const name = useStore(form.store, (s) => s.values.name);
  const selectedClass = useStore(form.store, (s) => s.values.selectedClass);
  const allocation = useStore(form.store, (s) => s.values.allocation);

  const classDef = CLASSES[selectedClass];
  const finalStats = useMemo<BaseStats>(() => {
    const bonuses = classDef.bonuses;
    const result: BaseStats = { ...allocation };
    for (const [k, v] of Object.entries(bonuses)) {
      result[k as StatKey] = Math.min(20, result[k as StatKey] + (v ?? 0));
    }
    return result;
  }, [allocation, classDef]);

  const spent = useMemo(
    () => (Object.keys(allocation) as StatKey[]).reduce((acc, k) => acc + (POINT_BUY_COST[allocation[k]] ?? 9), 0),
    [allocation],
  );
  const remaining = BUDGET - spent;

  const derived = useMemo(() => computeDerived(finalStats, 1), [finalStats]);

  const nameValid = name.trim().length >= 3 && name.trim().length <= 24;
  const canSubmit = nameValid && remaining >= 0;

  function setStat(key: StatKey, value: number) {
    const clamped = Math.max(STAT_MIN, Math.min(STAT_MAX, value));
    const current = form.getFieldValue("allocation");
    const next = { ...current, [key]: clamped };
    const nextSpent = (Object.keys(next) as StatKey[]).reduce((a, k) => a + (POINT_BUY_COST[next[k]] ?? 9), 0);
    if (nextSpent <= BUDGET) {
      form.setFieldValue("allocation", next);
      setError("");
    } else {
      setError("Not enough points.");
    }
  }

  function handleSubmit() {
    if (!canSubmit) return;
    form.handleSubmit();
  }

  return (
    <div className="flex gap-8">
      {/* Left: form */}
      <div className="flex-1 flex flex-col gap-5">
        {/* Name */}
        <div>
          <div className="text-white/50 text-xs mb-1 tracking-widest">NAME</div>
          <form.Field name="name">
            {(field) => (
              <input
                className={cn(
                  "w-full bg-black/40 border rounded px-3 py-2 text-sm text-white outline-none focus:border-blue-500 transition-colors",
                  nameValid || field.state.value === "" ? "border-white/20" : "border-red-500/60",
                )}
                placeholder="3–24 characters"
                value={field.state.value}
                maxLength={24}
                onChange={(e) => field.handleChange(e.target.value)}
                onBlur={field.handleBlur}
              />
            )}
          </form.Field>
        </div>

        {/* Class */}
        <div>
          <div className="text-white/50 text-xs mb-2 tracking-widest">CLASS</div>
          <div className="grid grid-cols-1 gap-1.5">
            {Object.entries(CLASSES).map(([key, cls]) => (
              <button
                key={key}
                onClick={() => form.setFieldValue("selectedClass", key)}
                className={cn(
                  "flex items-center gap-3 px-3 py-2 rounded border text-left transition-colors",
                  selectedClass === key
                    ? "bg-blue-950/60 border-blue-600/60 text-blue-300"
                    : "bg-black/30 border-white/10 text-white/60 hover:border-white/25",
                )}
              >
                <span className="text-xs font-bold w-20">{cls.label}</span>
                <span className="text-xs text-white/40">{cls.description}</span>
                <span className="ml-auto text-xs text-emerald-400">
                  {Object.entries(cls.bonuses)
                    .map(([k, v]) => `+${v} ${k.toUpperCase()}`)
                    .join(" ")}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* Stats */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <div className="text-white/50 text-xs tracking-widest">STAT ALLOCATION</div>
            <div
              className={cn(
                "text-xs font-mono",
                remaining < 0 ? "text-red-400" : remaining === 0 ? "text-emerald-400" : "text-yellow-400",
              )}
            >
              {remaining} pts remaining
            </div>
          </div>
          <div className="grid grid-cols-2 gap-x-4 gap-y-2">
            {STAT_LABELS.map(({ key, abbrev }) => {
              const bonus = classDef.bonuses[key] ?? 0;
              const val = allocation[key];
              const final = finalStats[key];
              return (
                <div key={key} className="flex items-center gap-2">
                  <span className="text-white/40 text-xs w-8">{abbrev}</span>
                  <button
                    onClick={() => setStat(key, val - 1)}
                    disabled={val <= STAT_MIN}
                    className="text-white/40 hover:text-white disabled:opacity-20 text-sm w-5 text-center"
                  >
                    −
                  </button>
                  <span className="text-white text-xs w-4 text-center">{val}</span>
                  <button
                    onClick={() => setStat(key, val + 1)}
                    disabled={val >= STAT_MAX || remaining <= 0}
                    className="text-white/40 hover:text-white disabled:opacity-20 text-sm w-5 text-center"
                  >
                    +
                  </button>
                  {bonus > 0 && <span className="text-emerald-400 text-xs">+{bonus}</span>}
                  <span className="text-white/30 text-xs ml-auto">{final}</span>
                </div>
              );
            })}
          </div>
        </div>

        {error && <div className="text-red-400 text-xs">{error}</div>}
      </div>

      {/* Right: preview */}
      <div className="w-56 flex flex-col">
        <div className="text-white/50 text-xs mb-3 tracking-widest">PREVIEW</div>
        <div className="bg-black/30 border border-white/10 rounded p-3 flex flex-col gap-1.5 text-xs">
          <div className="flex justify-between text-white/60 mb-1">
            <span>HP</span>
            <span className="text-white font-mono">{derived.maxHp}</span>
          </div>
          <div className="flex justify-between text-white/60">
            <span>Mana</span>
            <span className="text-white font-mono">{derived.maxMana}</span>
          </div>
          <div className="border-t border-white/10 mt-1 mb-1" />
          <div className="flex justify-between text-white/60">
            <span>Melee dmg</span>
            <span className="text-white font-mono">
              {derived.meleeDmg >= 0 ? `+${derived.meleeDmg}` : derived.meleeDmg}
            </span>
          </div>
          <div className="flex justify-between text-white/60">
            <span>Ranged dmg</span>
            <span className="text-white font-mono">
              {derived.rangedDmg >= 0 ? `+${derived.rangedDmg}` : derived.rangedDmg}
            </span>
          </div>
          <div className="flex justify-between text-white/60">
            <span>Spell dmg</span>
            <span className="text-white font-mono">
              {derived.spellDmg >= 0 ? `+${derived.spellDmg}` : derived.spellDmg}
            </span>
          </div>
          <div className="border-t border-white/10 mt-1 mb-1" />
          <div className="flex justify-between text-white/60">
            <span>Crit chance</span>
            <span className="text-white font-mono">{derived.critChance}%</span>
          </div>
          <div className="flex justify-between text-white/60">
            <span>Dodge</span>
            <span className="text-white font-mono">{derived.dodgePct}%</span>
          </div>
          <div className="flex justify-between text-white/60">
            <span>Magic resist</span>
            <span className="text-white font-mono">{derived.magicResist}%</span>
          </div>
          <div className="flex justify-between text-white/60">
            <span>Initiative</span>
            <span className="text-white font-mono">
              {derived.initiative >= 0 ? `+${derived.initiative}` : derived.initiative}
            </span>
          </div>
        </div>

        <div className="mt-auto pt-5 flex flex-col gap-2">
          <Button variant="secondary" className="w-full font-mono" disabled={!canSubmit} onClick={handleSubmit}>
            Create
          </Button>
          {!required && onCancel && (
            <Button variant="ghost" className="w-full font-mono text-white/40" onClick={onCancel}>
              Cancel
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
