import { useCallback, useEffect, useState } from "react";
import { api, WorldStatsDto } from "../api";
import { useI18n, useT, type TranslationKey } from "../i18n";

function Icon({ d, size = 16 }: { d: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={d} />
    </svg>
  );
}

const I = {
  world: "M3 7l9-4 9 4M3 7v10l9 4m-9-14l9 4m9-4v10l-9 4m0-14v14",
  chunk: "M3 7l9-4 9 4M3 7v10l9 4m-9-14l9 4m9-4v10l-9 4m0-14v14",
  player: "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z",
  seed: "M12 2a10 10 0 100 20A10 10 0 0012 2zm0 0v20M2 12h20",
  add: "M12 5v14M5 12h14",
  active: "M5 13l4 4L19 7",
};

function Badge({ children, color }: { children: React.ReactNode; color: string }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold ${color}`}>
      {children}
    </span>
  );
}

function WorldCard({ world, onRefresh: _onRefresh }: { world: WorldStatsDto; onRefresh: () => void }) {
  const { locale, t } = useI18n();
  const date = new Date(world.createdAt).toLocaleDateString(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  return (
    <div
      className={`bg-[#1A222C] rounded-xl border p-5 transition-colors ${
        world.isActive ? "border-[#3C50E0]" : "border-[#2E3A4E]"
      }`}
    >
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="flex items-center gap-2.5 min-w-0">
          <div
            className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
              world.isActive ? "bg-[#3C50E0]/20 text-[#3C50E0]" : "bg-[#2E3A4E] text-[#8A99AF]"
            }`}
          >
            <Icon d={I.world} size={18} />
          </div>
          <div className="min-w-0">
            <p className="text-white font-semibold text-[15px] truncate">{world.name}</p>
            <p className="text-[11px] text-[#8A99AF]">{t("worlds.created", date)}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {world.isActive && (
            <Badge color="bg-[#3C50E0]/20 text-[#3C50E0]">
              <Icon d={I.active} size={10} />
              <span className="ml-1">{t("worlds.active")}</span>
            </Badge>
          )}
          <Badge color="bg-[#2E3A4E] text-[#8A99AF]">{world.generator}</Badge>
        </div>
      </div>
      <div className="grid grid-cols-3 gap-3 p-3 bg-[#0E1726] rounded-lg">
        <div className="text-center">
          <p className="text-[10px] uppercase tracking-widest text-[#8A99AF] mb-1">{t("worlds.seed")}</p>
          <p className="text-white font-mono text-sm font-semibold tabular-nums">{world.seed}</p>
        </div>
        <div className="text-center border-x border-[#2E3A4E]">
          <p className="text-[10px] uppercase tracking-widest text-[#8A99AF] mb-1">{t("worlds.chunks")}</p>
          <p className="text-white font-semibold text-sm tabular-nums">{world.chunkCount.toLocaleString()}</p>
        </div>
        <div className="text-center">
          <p className="text-[10px] uppercase tracking-widest text-[#8A99AF] mb-1">{t("worlds.players")}</p>
          <p className="text-white font-semibold text-sm tabular-nums">{world.playerCount}</p>
        </div>
      </div>
    </div>
  );
}

function CreateWorldForm({ onCreated }: { onCreated: () => void }) {
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
        <Icon d={I.add} size={16} />
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

export function WorldsPage() {
  const t = useT();
  const [worlds, setWorlds] = useState<WorldStatsDto[] | null>(null);
  const [errorKey, setErrorKey] = useState<TranslationKey | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api.worlds.list();
      setWorlds(data);
      setErrorKey(null);
    } catch {
      setErrorKey("worlds.failedToLoad");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (errorKey) return <p className="text-red-400 text-sm">{t(errorKey)}</p>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-[#8A99AF] text-xs">
            {worlds ? t(worlds.length === 1 ? "worlds.countOne" : "worlds.countMany", worlds.length) : "…"}
          </p>
          <p className="text-[11px] text-[#4A5568] mt-0.5">
            {t("worlds.switchHintBefore")} <code className="font-mono">MICRAFT_WORLD_NAME</code>{" "}
            {t("worlds.switchHintAfter")}
          </p>
        </div>
        <CreateWorldForm onCreated={load} />
      </div>

      {worlds === null ? (
        <p className="text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>
      ) : worlds.length === 0 ? (
        <p className="text-[#8A99AF] text-sm">{t("worlds.none")}</p>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
          {worlds.map((w) => (
            <WorldCard key={w.name} world={w} onRefresh={load} />
          ))}
        </div>
      )}
    </div>
  );
}
