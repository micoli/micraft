import { useState, useMemo } from "react";
import { useNavigate } from "react-router";
import { Button } from "../primitives/Button";
import { Panel } from "../primitives/Panel";
import { cn } from "../primitives/cn";
import { getUsers, saveUsers, getLastUser, getAccountEmail } from "../lib/authStorage";
import { postApiCharacterRpgcreate } from "../generated/api/requests";

const NAME_DATA = {
  first_start: [
    "gar",
    "ald",
    "bran",
    "ced",
    "eth",
    "wil",
    "tho",
    "mar",
    "el",
    "cor",
    "aed",
    "bal",
    "dor",
    "fin",
    "gal",
    "har",
    "ing",
    "jor",
    "ken",
    "lor",
    "mir",
    "ned",
    "osw",
    "per",
    "quin",
    "ran",
    "sel",
    "tor",
    "uld",
    "val",
    "wyn",
    "yor",
    "arn",
    "bea",
    "col",
    "den",
    "eir",
    "fre",
    "gis",
    "hal",
    "ism",
    "kae",
    "leof",
    "mor",
    "oth",
    "ric",
    "sig",
    "tan",
    "ulf",
    "cas",
  ],
  first_end: [
    "ric",
    "win",
    "dor",
    "ton",
    "lan",
    "ren",
    "bert",
    "mund",
    "hart",
    "den",
    "ricc",
    "wyn",
    "rid",
    "gar",
    "mir",
    "rad",
    "wulf",
    "lin",
    "ran",
    "vald",
    "son",
    "mar",
    "fin",
    "las",
    "dir",
    "vor",
    "lok",
    "ris",
    "lom",
    "rol",
    "rik",
    "rim",
    "tal",
    "fer",
    "gis",
    "hem",
    "bar",
    "gir",
    "han",
    "wir",
    "lof",
    "bur",
    "nor",
    "tru",
    "hal",
    "tan",
    "eld",
    "wyns",
    "gund",
    "bran",
  ],
  surname_prefix: [
    "Iron",
    "Stone",
    "Wood",
    "Raven",
    "Silver",
    "Green",
    "Black",
    "Red",
    "White",
    "Gold",
    "Ashen",
    "Frost",
    "Storm",
    "Flint",
    "Steel",
    "Crow",
    "Wolf",
    "Oak",
    "Thorn",
    "Hawk",
    "Bear",
    "Fox",
    "Deer",
    "Swan",
    "Falcon",
    "Bright",
    "Dusk",
    "Shade",
    "Grim",
    "Swift",
    "Rain",
    "Snow",
    "Wind",
    "Bronze",
    "Ember",
    "River",
    "Clear",
    "Leaf",
    "Burn",
    "Mist",
    "Wild",
    "Glen",
    "Pine",
    "Briar",
    "Drake",
    "Vale",
    "Hearth",
    "Lark",
    "Sun",
    "Shadow",
    "Cinder",
  ],
  surname_suffix: [
    "forge",
    "bridge",
    "field",
    "guard",
    "helm",
    "wood",
    "stone",
    "brook",
    "dale",
    "ton",
    "hall",
    "worth",
    "well",
    "glen",
    "moor",
    "crest",
    "shore",
    "hill",
    "ford",
    "holt",
    "mere",
    "vale",
    "ridge",
    "burn",
    "fall",
    "gate",
    "croft",
    "stead",
    "mont",
    "peak",
    "cliff",
    "bank",
    "bloom",
    "thorn",
    "grove",
    "wick",
    "port",
    "den",
    "glade",
    "path",
    "keep",
    "haven",
    "loch",
    "marsh",
    "fen",
    "bay",
    "watch",
    "run",
    "firth",
    "barrow",
  ],
};

function pick(arr: string[]): string {
  return arr[Math.floor(Math.random() * arr.length)];
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function generateFantasyName(): string {
  const first = capitalize(pick(NAME_DATA.first_start) + pick(NAME_DATA.first_end));
  const last = capitalize(pick(NAME_DATA.surname_prefix) + pick(NAME_DATA.surname_suffix));
  const name = `${first} ${last}`;
  return name.length <= 24 ? name : first;
}

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

export function CharacterRPGCreationScreen() {
  const navigate = useNavigate();
  const username = getLastUser();
  const accountKey = getAccountEmail() || username;

  const [name, setName] = useState(() => generateFantasyName());
  const [selectedClass, setSelectedClass] = useState("WARRIOR");
  const [allocation, setAllocation] = useState<BaseStats>({ str: 8, dex: 8, intel: 8, wis: 8, con: 8, cha: 8 });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

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
    const next = { ...allocation, [key]: clamped };
    const nextSpent = (Object.keys(next) as StatKey[]).reduce((a, k) => a + (POINT_BUY_COST[next[k]] ?? 9), 0);
    if (nextSpent <= BUDGET) {
      setAllocation(next);
      setError("");
    } else {
      setError("Not enough points.");
    }
  }

  function rollStats() {
    const stats: BaseStats = { str: 8, dex: 8, intel: 8, wis: 8, con: 8, cha: 8 };
    const keys = Object.keys(stats) as StatKey[];
    let budget = BUDGET;
    let stuck = false;
    while (!stuck) {
      const shuffled = [...keys].sort(() => Math.random() - 0.5);
      stuck = true;
      for (const k of shuffled) {
        const cur = stats[k];
        if (cur >= STAT_MAX) continue;
        const cost = (POINT_BUY_COST[cur + 1] ?? 9) - (POINT_BUY_COST[cur] ?? 0);
        if (cost <= budget) {
          stats[k] = cur + 1;
          budget -= cost;
          stuck = false;
          break;
        }
      }
    }
    setAllocation(stats);
    setError("");
  }

  async function handleSubmit() {
    if (!canSubmit) return;
    const trimmed = name.trim();
    setLoading(true);
    try {
      const { data, response } = await postApiCharacterRpgcreate({
        body: {
          playerName: trimmed,
          skin: "player",
          characterClass: selectedClass,
          str: allocation.str,
          dex: allocation.dex,
          intel: allocation.intel,
          wis: allocation.wis,
          con: allocation.con,
          cha: allocation.cha,
          email: accountKey,
        },
      });
      if (!response?.ok || !data) {
        setError("Creation failed.");
        setLoading(false);
        return;
      }
      const users = getUsers();
      if (!users[accountKey]) users[accountKey] = [];
      if (!users[accountKey].some((c) => c.name === trimmed)) users[accountKey].push({ name: trimmed, id: data.id });
      saveUsers(users);
      navigate("/chars");
    } catch {
      setError("Connection error.");
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className="min-w-[760px]">
        <div className="text-blue-300 tracking-widest mb-6 font-mono">CREATE RPG CHARACTER</div>
        <div className="flex gap-8">
          <div className="flex-1 flex flex-col gap-5">
            <div>
              <div className="text-white/50 text-xs mb-1 tracking-widest">NAME</div>
              <div className="flex gap-2">
                <input
                  className={cn(
                    "flex-1 bg-black/40 border rounded px-3 py-2 text-sm text-white outline-none focus:border-blue-500 transition-colors",
                    nameValid || name === "" ? "border-white/20" : "border-red-500/60",
                  )}
                  placeholder="3–24 characters"
                  value={name}
                  maxLength={24}
                  onChange={(e) => setName(e.target.value)}
                />
                <button
                  title="Generate random name"
                  onClick={() => setName(generateFantasyName())}
                  className="px-2 text-white/40 hover:text-white border border-white/10 rounded bg-black/30 hover:border-white/25 transition-colors text-sm"
                >
                  ↺
                </button>
              </div>
            </div>

            <div>
              <div className="text-white/50 text-xs mb-2 tracking-widest">CLASS</div>
              <div className="grid grid-cols-1 gap-1.5">
                {Object.entries(CLASSES).map(([key, cls]) => (
                  <button
                    key={key}
                    onClick={() => setSelectedClass(key)}
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

            <div>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <div className="text-white/50 text-xs tracking-widest">STAT ALLOCATION</div>
                  <button
                    title="Roll random stats"
                    onClick={rollStats}
                    className="px-1.5 py-0.5 text-white/40 hover:text-white border border-white/10 rounded bg-black/30 hover:border-white/25 transition-colors text-xs"
                  >
                    Roll
                  </button>
                </div>
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

          <div className="w-56 flex flex-col">
            <div className="text-white/50 text-xs mb-3 tracking-widest">PREVIEW</div>
            <div className="bg-black/30 border border-white/10 rounded p-3 flex flex-col gap-1.5 text-xs">
              {[
                ["HP", derived.maxHp],
                ["Mana", derived.maxMana],
              ].map(([k, v]) => (
                <div key={String(k)} className="flex justify-between text-white/60">
                  <span>{k}</span>
                  <span className="text-white font-mono">{v}</span>
                </div>
              ))}
              <div className="border-t border-white/10 my-1" />
              {[
                ["Melee dmg", derived.meleeDmg],
                ["Ranged dmg", derived.rangedDmg],
                ["Spell dmg", derived.spellDmg],
              ].map(([k, v]) => (
                <div key={String(k)} className="flex justify-between text-white/60">
                  <span>{k}</span>
                  <span className="text-white font-mono">{Number(v) >= 0 ? `+${v}` : v}</span>
                </div>
              ))}
              <div className="border-t border-white/10 my-1" />
              {[
                ["Crit chance", `${derived.critChance}%`],
                ["Dodge", `${derived.dodgePct}%`],
                ["Magic resist", `${derived.magicResist}%`],
                ["Initiative", Number(derived.initiative) >= 0 ? `+${derived.initiative}` : derived.initiative],
              ].map(([k, v]) => (
                <div key={String(k)} className="flex justify-between text-white/60">
                  <span>{k}</span>
                  <span className="text-white font-mono">{v}</span>
                </div>
              ))}
            </div>

            <div className="mt-auto pt-5 flex flex-col gap-2">
              <Button
                variant="secondary"
                className="w-full font-mono"
                disabled={!canSubmit || loading}
                onClick={() => void handleSubmit()}
              >
                {loading ? "Creating…" : "Create"}
              </Button>
              <Button variant="ghost" className="w-full font-mono text-white/40" onClick={() => navigate("/chars")}>
                Cancel
              </Button>
            </div>
          </div>
        </div>
      </Panel>
    </div>
  );
}
