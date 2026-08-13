import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { useState } from "react";
import { SaveButton } from "../../../primitives/SaveButton";

export function KeybindingsTab({
  file,
  onSave,
}: {
  file: PlayerFile;
  onSave: (kb: Record<string, string[]>) => Promise<void>;
}) {
  const t = useT();
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
        placeholder={t("players.filterActions")}
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
      <SaveButton saving={saving} saved={saved} onClick={save} />
    </div>
  );
}
