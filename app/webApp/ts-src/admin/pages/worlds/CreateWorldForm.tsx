import { useT } from "../../i18n";
import { useState } from "react";
import { api } from "../../api";
import { Icon } from "../../../primitives/Icon";
import { ICONS } from "../../../primitives/icons";

export function CreateWorldForm({ onCreated }: { onCreated: () => void }) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [seed, setSeed] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const randomSeed = () => setSeed(String(Math.floor(Math.random() * 2_147_483_647)));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return setError(t("worlds.nameRequired"));
    if (!/^[a-zA-Z0-9_-]+$/.test(name)) return setError(t("worlds.nameRule"));
    const seedNum = seed === "" ? 42 : Number(seed);
    if (isNaN(seedNum)) return setError(t("worlds.seedMustBeNumber"));
    setSaving(true);
    try {
      const r = await api.worlds.create(name.trim(), seedNum);
      if (r.status === 409) return setError(t("worlds.alreadyExists"));
      if (!r.ok) return setError(t("worlds.serverError"));
      setName("");
      setSeed("");
      setOpen(false);
      onCreated();
    } finally {
      setSaving(false);
    }
  };

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-[#3C50E0] hover:bg-[#3446c7] text-white text-sm font-medium transition-colors"
      >
        <Icon d={ICONS.add} size={16} />
        {t("worlds.create")}
      </button>
    );
  }

  return (
    <div className="bg-[#1A222C] rounded-xl border border-[#3C50E0] p-5">
      <h3 className="text-white font-semibold text-[15px] mb-4">{t("worlds.newWorld")}</h3>
      <form onSubmit={submit} className="space-y-3">
        <div>
          <label className="block text-[11px] uppercase tracking-widest text-[#8A99AF] mb-1.5">
            {t("worlds.name")}
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t("worlds.namePlaceholder")}
            className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0] transition-colors"
          />
        </div>
        <div>
          <label className="block text-[11px] uppercase tracking-widest text-[#8A99AF] mb-1.5">
            {t("worlds.seed")}
          </label>
          <div className="flex gap-2">
            <input
              type="number"
              value={seed}
              onChange={(e) => setSeed(e.target.value)}
              placeholder="42"
              className="flex-1 bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0] transition-colors font-mono"
            />
            <button
              type="button"
              onClick={randomSeed}
              className="px-3 py-2 rounded-lg border border-[#2E3A4E] text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E] text-xs transition-colors"
            >
              {t("worlds.random")}
            </button>
          </div>
        </div>
        {error && <p className="text-red-400 text-xs">{error}</p>}
        <div className="flex gap-2 pt-1">
          <button
            type="submit"
            disabled={saving}
            className="flex-1 py-2 rounded-lg bg-[#3C50E0] hover:bg-[#3446c7] text-white text-sm font-medium transition-colors disabled:opacity-50"
          >
            {saving ? t("worlds.creating") : t("worlds.createShort")}
          </button>
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              setError(null);
            }}
            className="px-4 py-2 rounded-lg border border-[#2E3A4E] text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E] text-sm transition-colors"
          >
            {t("common.cancel")}
          </button>
        </div>
      </form>
    </div>
  );
}
