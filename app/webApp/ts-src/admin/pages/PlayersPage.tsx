import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../../primitives/Tabs";
import { api, BaseStats, PlayerFile } from "../api";

const CHARACTER_CLASSES = ["WARRIOR", "MAGE", "RANGER", "ROGUE", "CLERIC"];

// ── Shared design atoms ───────────────────────────────────────────────────────
function SaveBtn({ saving, saved, onClick }: { saving: boolean; saved: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      disabled={saving}
      className="px-4 py-1.5 rounded-lg text-sm font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
    >
      {saving ? "Saving…" : saved ? "Saved ✓" : "Save"}
    </button>
  );
}

function TextInput({
  id,
  value,
  onChange,
  type = "text",
  min,
  max,
}: {
  id?: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  min?: number;
  max?: number;
}) {
  return (
    <input
      id={id}
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      min={min}
      max={max}
      className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-[#3C50E0] transition-colors"
    />
  );
}

function Field({ label, htmlFor, children }: { label: string; htmlFor?: string; children: React.ReactNode }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="block text-xs font-medium text-[#8A99AF] mb-1.5">
        {label}
      </label>
      {children}
    </div>
  );
}

function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!value)}
      className={`w-9 h-[14px] rounded-full transition-colors relative shrink-0 overflow-visible ${value ? "bg-[#3C50E0]" : "bg-[#2E3A4E]"}`}
    >
      <span
        className={`absolute top-1/2 -translate-y-1/2 left-0 w-[18px] h-[18px] rounded-full bg-white shadow-sm transition-transform ${value ? "translate-x-[20px]" : "translate-x-0.5"}`}
      />
    </button>
  );
}

function BoolRow({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-[#2E3A4E] last:border-0">
      <span className="text-sm text-[#8A99AF]">{label}</span>
      <Toggle value={value} onChange={onChange} />
    </div>
  );
}

const KNOWN_LOCALES = ["en", "fr", "de", "es", "ja", "zh", "pt", "ru", "it", "nl"];

// ── Tabs ──────────────────────────────────────────────────────────────────────
function PreferencesTab({
  file,
  onSave,
}: {
  file: PlayerFile;
  onSave: (prefs: Partial<PlayerFile["state"]>) => Promise<void>;
}) {
  const s = file.state;
  const [skin, setSkin] = useState(s.skin);
  const [language, setLanguage] = useState(s.language);
  const [fov, setFov] = useState(s.fieldOfView);
  const [shaders, setShaders] = useState(s.shadersEnabled);
  const [favicon, setFavicon] = useState(s.animatedFavicon);
  const [godMode, setGodMode] = useState(s.godMode);
  const [lightBoost, setLightBoost] = useState(s.lightBoostEnabled);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [availableSkins, setAvailableSkins] = useState<string[]>([]);

  useEffect(() => {
    api.skins
      .list()
      .then(setAvailableSkins)
      .catch(() => {});
  }, []);

  const skinOptions = Array.from(new Set([...availableSkins, skin])).sort();
  const langOptions = Array.from(new Set([...KNOWN_LOCALES, language])).sort();

  const save = async () => {
    setSaving(true);
    await onSave({
      skin,
      language,
      fieldOfView: fov,
      shadersEnabled: shaders,
      animatedFavicon: favicon,
      godMode,
      lightBoostEnabled: lightBoost,
    });
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  const selectCls =
    "w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-[#3C50E0] transition-colors";

  return (
    <div className="p-5 space-y-4">
      <Field label="Skin" htmlFor="skin">
        <select id="skin" value={skin} onChange={(e) => setSkin(e.target.value)} className={selectCls}>
          {skinOptions.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Language" htmlFor="lang">
        <select id="lang" value={language} onChange={(e) => setLanguage(e.target.value)} className={selectCls}>
          {langOptions.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>
      </Field>
      <Field label={`Field of View — ${fov}°`} htmlFor="fov">
        <input
          id="fov"
          type="range"
          min={30}
          max={120}
          step={1}
          value={fov}
          onChange={(e) => setFov(Number(e.target.value))}
          className="w-full accent-[#3C50E0]"
        />
        <div className="flex justify-between text-xs text-[#4A5568] mt-0.5">
          <span>30°</span>
          <span>120°</span>
        </div>
      </Field>
      <div className="pt-1">
        <BoolRow label="Shaders" value={shaders} onChange={setShaders} />
        <BoolRow label="Animated Favicon" value={favicon} onChange={setFavicon} />
        <BoolRow label="God Mode" value={godMode} onChange={setGodMode} />
        <BoolRow label="Light Boost" value={lightBoost} onChange={setLightBoost} />
      </div>
      <SaveBtn saving={saving} saved={saved} onClick={save} />
    </div>
  );
}

function KeybindingsTab({
  file,
  onSave,
}: {
  file: PlayerFile;
  onSave: (kb: Record<string, string[]>) => Promise<void>;
}) {
  const [bindings, setBindings] = useState<Record<string, string[]>>({ ...file.keybindings });
  const [filter, setFilter] = useState("");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const save = async () => {
    setSaving(true);
    await onSave(bindings);
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  const updateKey = (action: string, idx: number, value: string) =>
    setBindings((prev) => ({ ...prev, [action]: prev[action].map((k, i) => (i === idx ? value : k)) }));
  const addKey = (action: string) => setBindings((prev) => ({ ...prev, [action]: [...(prev[action] ?? []), ""] }));
  const removeKey = (action: string, idx: number) =>
    setBindings((prev) => ({ ...prev, [action]: prev[action].filter((_, i) => i !== idx) }));

  const q = filter.toLowerCase();
  const sorted = Object.entries(bindings)
    .filter(([action]) => !q || action.toLowerCase().includes(q))
    .sort(([a], [b]) => a.localeCompare(b));

  const groups: Record<string, [string, string[]][]> = {};
  for (const entry of sorted) {
    const grp = entry[0].split("_")[0];
    (groups[grp] ??= []).push(entry);
  }

  return (
    <div className="p-5">
      <input
        type="text"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        placeholder="Filter actions…"
        className="w-full mb-4 bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-[#3C50E0] transition-colors"
      />
      <div className="space-y-0 mb-5 max-h-[48vh] overflow-y-auto">
        {Object.entries(groups).map(([grp, entries]) => (
          <div key={grp}>
            <div className="text-[10px] uppercase tracking-widest font-semibold text-[#4A5568] py-1.5 sticky top-0 bg-[#1A222C]">
              {grp}
            </div>
            {entries.map(([action, keys]) => (
              <div key={action} className="flex items-start gap-3 py-2 border-b border-[#2E3A4E] last:border-0">
                <span className="text-xs text-[#8A99AF] w-44 shrink-0 pt-1 font-mono">{action.replace(/_/g, " ")}</span>
                <div className="flex flex-wrap gap-1.5 flex-1">
                  {keys.map((k, i) => (
                    <div key={i} className="flex items-center gap-0.5">
                      <input
                        value={k}
                        onChange={(e) => updateKey(action, i, e.target.value)}
                        className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white w-24 focus:outline-none focus:border-[#3C50E0]"
                      />
                      <button
                        onClick={() => removeKey(action, i)}
                        className="text-[#4A5568] hover:text-red-400 text-xs px-0.5 transition-colors"
                      >
                        ×
                      </button>
                    </div>
                  ))}
                  <button
                    onClick={() => addKey(action)}
                    className="text-[#4A5568] hover:text-[#3C50E0] text-xs border border-[#2E3A4E] rounded px-1.5 py-0.5 transition-colors"
                  >
                    +
                  </button>
                </div>
              </div>
            ))}
          </div>
        ))}
      </div>
      <SaveBtn saving={saving} saved={saved} onClick={save} />
    </div>
  );
}

function computeDerived(s: BaseStats, level: number) {
  const f = (v: number) => Math.floor((v - 10) / 2);
  return {
    maxHp: Math.max(1, Math.floor(f(s.con) * level + 10)),
    maxMana: s.wis * 5,
    meleeDmg: f(s.str),
    rangedDmg: f(s.dex),
    spellDmg: f(s.intel),
    critChancePct: (5 + s.dex * 0.2).toFixed(1),
    dodgePct: Math.min(s.dex * 2.5, 75).toFixed(1),
    magicResistPct: Math.max((s.wis - 10) * 2, 0).toFixed(1),
    armorClass: 10 + f(s.dex),
    hpRegenPerSec: (s.con / 10).toFixed(2),
    manaRegenPerSec: (s.wis / 20).toFixed(2),
    maxTokens: Math.floor(level / 4) + 1,
  };
}

function RpgTab({ file, onSave }: { file: PlayerFile; onSave: (rpg: Record<string, unknown>) => Promise<void> }) {
  const cd = file.state.characterData!;
  const [cls, setCls] = useState(cd.characterClass);
  const [stats, setStats] = useState({ ...cd.baseStats });
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const save = async () => {
    setSaving(true);
    await onSave({ characterClass: cls, ...stats });
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  const derived = computeDerived(stats, cd.level);

  const statRow = (name: keyof typeof stats, label: string) => (
    <div className="flex items-center gap-3 py-2 border-b border-[#2E3A4E] last:border-0">
      <span className="text-xs text-[#8A99AF] w-24">{label}</span>
      <input
        type="range"
        min={1}
        max={20}
        value={stats[name]}
        onChange={(e) => setStats((p) => ({ ...p, [name]: Number(e.target.value) }))}
        className="flex-1 accent-[#3C50E0]"
      />
      <span className="text-sm text-[#3C50E0] w-6 text-right tabular-nums font-semibold">{stats[name]}</span>
    </div>
  );

  return (
    <div className="p-5 space-y-5">
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">Class</p>
        <div className="flex flex-wrap gap-2">
          {CHARACTER_CLASSES.map((c) => (
            <button
              key={c}
              onClick={() => setCls(c)}
              className={`px-3 py-1 rounded-lg text-xs font-medium border transition-colors ${
                cls === c
                  ? "bg-[#3C50E0]/20 border-[#3C50E0] text-[#818CF8]"
                  : "border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0]/50 hover:text-white"
              }`}
            >
              {c}
            </button>
          ))}
        </div>
      </div>
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">Base Stats</p>
        {statRow("str", "Strength")}
        {statRow("dex", "Dexterity")}
        {statRow("intel", "Intellect")}
        {statRow("wis", "Wisdom")}
        {statRow("con", "Constitution")}
        {statRow("cha", "Charisma")}
      </div>
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">
          Derived Stats <span className="text-[#4A5568] normal-case font-normal">(Lv {cd.level})</span>
        </p>
        <div className="grid grid-cols-2 gap-x-4">
          {[
            ["Max HP", derived.maxHp],
            ["Max Mana", derived.maxMana],
            ["Melee Dmg", `+${derived.meleeDmg}`],
            ["Ranged Dmg", `+${derived.rangedDmg}`],
            ["Spell Dmg", `+${derived.spellDmg}`],
            ["Crit Chance", `${derived.critChancePct}%`],
            ["Dodge", `${derived.dodgePct}%`],
            ["Magic Resist", `${derived.magicResistPct}%`],
            ["Armor Class", derived.armorClass],
            ["HP Regen/s", derived.hpRegenPerSec],
            ["Mana Regen/s", derived.manaRegenPerSec],
            ["Max Tokens", derived.maxTokens],
          ].map(([label, value]) => (
            <div key={String(label)} className="flex justify-between py-1 border-b border-[#2E3A4E] text-xs">
              <span className="text-[#8A99AF]">{label}</span>
              <span className="text-white tabular-nums font-medium">{value}</span>
            </div>
          ))}
        </div>
      </div>
      <p className="text-xs text-[#4A5568]">
        {cd.currentHp} / {derived.maxHp} HP · {cd.currentMana} / {derived.maxMana} Mana
      </p>
      <SaveBtn saving={saving} saved={saved} onClick={save} />
    </div>
  );
}

// ── Player detail panel ───────────────────────────────────────────────────────
function PlayerDetail({
  name,
  onBack,
  onRenamed,
}: {
  name: string;
  onBack: () => void;
  onRenamed: (newName: string) => void;
}) {
  const [file, setFile] = useState<PlayerFile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [newName, setNewName] = useState(name);
  const [renameErr, setRenameErr] = useState<string | null>(null);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setFile(null);
    setNewName(name);
    api.players
      .get(name)
      .then(setFile)
      .catch(() => setError("Failed to load"));
  }, [name]);

  const doRename = async () => {
    if (!newName || newName === name) {
      setRenaming(false);
      return;
    }
    const r = await api.players.rename(name, newName);
    if (r.ok) {
      onRenamed(newName);
    } else {
      setRenameErr("Rename failed");
    }
    setRenaming(false);
  };

  if (error) return <div className="p-5 text-red-400 text-sm">{error}</div>;
  if (!file) return <div className="p-5 text-[#8A99AF] text-sm animate-pulse">Loading…</div>;

  const hasRpg = !!file.state.characterData;

  return (
    <div>
      <div className="flex items-center gap-3 px-5 py-3.5 border-b border-[#2E3A4E]">
        <button onClick={onBack} className="text-[#4A5568] hover:text-white text-sm transition-colors">
          ←
        </button>
        {renaming ? (
          <div className="flex items-center gap-1.5 flex-1">
            <input
              autoFocus
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") doRename();
                if (e.key === "Escape") {
                  setRenaming(false);
                  setNewName(name);
                }
              }}
              className="bg-[#0E1726] border border-[#3C50E0] rounded px-2 py-0.5 text-sm text-white focus:outline-none"
            />
            <button onClick={doRename} className="text-xs text-[#3C50E0] hover:text-white transition-colors">
              ✓
            </button>
            <button
              onClick={() => {
                setRenaming(false);
                setNewName(name);
              }}
              className="text-xs text-[#4A5568] hover:text-white transition-colors"
            >
              ✕
            </button>
          </div>
        ) : (
          <>
            <span className="text-sm font-semibold text-white">{name}</span>
            <button
              onClick={() => setRenaming(true)}
              className="text-[10px] text-[#4A5568] hover:text-[#8A99AF] transition-colors"
            >
              ✎
            </button>
          </>
        )}
        {renameErr && <span className="text-xs text-red-400">{renameErr}</span>}
        <span className="text-[10px] font-medium bg-[#2E3A4E] text-[#8A99AF] px-2 py-0.5 rounded-full ml-auto">
          {hasRpg ? `RPG · ${file.state.characterData!.characterClass}` : "classic"}
        </span>
        {file.state.email && (
          <Link
            to={`/admin/users?u=${encodeURIComponent(file.state.email)}`}
            className="text-[10px] text-[#818CF8] hover:text-white transition-colors font-mono truncate max-w-[180px]"
            title={`Owner: ${file.state.email}`}
          >
            {file.state.email}
          </Link>
        )}
      </div>
      <Tabs defaultValue="prefs">
        <TabsList className="px-5 border-b border-[#2E3A4E] rounded-none bg-transparent gap-1">
          <TabsTrigger
            value="prefs"
            className="text-xs data-[state=active]:text-white data-[state=active]:border-b-2 data-[state=active]:border-[#3C50E0] rounded-none pb-2 px-1 text-[#8A99AF] transition-colors"
          >
            Preferences
          </TabsTrigger>
          <TabsTrigger
            value="kb"
            className="text-xs data-[state=active]:text-white data-[state=active]:border-b-2 data-[state=active]:border-[#3C50E0] rounded-none pb-2 px-1 text-[#8A99AF] transition-colors"
          >
            Keybindings
          </TabsTrigger>
          {hasRpg && (
            <TabsTrigger
              value="rpg"
              className="text-xs data-[state=active]:text-white data-[state=active]:border-b-2 data-[state=active]:border-[#3C50E0] rounded-none pb-2 px-1 text-[#8A99AF] transition-colors"
            >
              RPG
            </TabsTrigger>
          )}
        </TabsList>
        <TabsContent value="prefs">
          <PreferencesTab
            file={file}
            onSave={async (prefs) => {
              const r = await api.players.savePreferences(name, prefs);
              if (!r.ok) throw new Error("Save failed");
            }}
          />
        </TabsContent>
        <TabsContent value="kb">
          <KeybindingsTab
            file={file}
            onSave={async (kb) => {
              const r = await api.players.saveKeybindings(name, kb);
              if (!r.ok) throw new Error("Save failed");
            }}
          />
        </TabsContent>
        {hasRpg && (
          <TabsContent value="rpg">
            <RpgTab
              file={file}
              onSave={async (rpg) => {
                const r = await api.players.saveRpg(name, rpg as Parameters<typeof api.players.saveRpg>[1]);
                if (!r.ok) throw new Error("Save failed");
              }}
            />
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────
export function PlayersPage() {
  const [searchParams] = useSearchParams();
  const [players, setPlayers] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<string | null>(searchParams.get("p"));

  useEffect(() => {
    api.players.list().then((p) => {
      setPlayers(p.sort());
      setLoading(false);
    });
  }, []);

  const handleRenamed = (oldName: string, newName: string) => {
    setPlayers((prev) => prev.map((p) => (p === oldName ? newName : p)).sort());
    setSelected(newName);
  };

  return (
    <div className="flex gap-4 h-full">
      {/* List */}
      <div className="w-52 shrink-0 bg-[#1A222C] border border-[#2E3A4E] rounded-xl overflow-hidden self-start">
        <p className="px-4 py-3 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] border-b border-[#2E3A4E]">
          Players
        </p>
        {loading ? (
          <p className="px-4 py-4 text-[#8A99AF] text-sm animate-pulse">Loading…</p>
        ) : players.length === 0 ? (
          <p className="px-4 py-4 text-[#4A5568] text-sm">No players</p>
        ) : (
          <div>
            {players.map((p) => (
              <button
                key={p}
                onClick={() => setSelected(p)}
                className={`w-full text-left px-4 py-2.5 text-sm transition-colors border-b border-[#2E3A4E] last:border-0 ${
                  selected === p
                    ? "bg-[#3C50E0]/20 text-[#818CF8]"
                    : "text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white"
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Detail */}
      <div className="flex-1 bg-[#1A222C] border border-[#2E3A4E] rounded-xl overflow-hidden self-start min-h-[200px]">
        {selected ? (
          <PlayerDetail
            key={selected}
            name={selected}
            onBack={() => setSelected(null)}
            onRenamed={(newName) => handleRenamed(selected, newName)}
          />
        ) : (
          <div className="p-6 text-[#4A5568] text-sm">Select a player to edit</div>
        )}
      </div>
    </div>
  );
}
