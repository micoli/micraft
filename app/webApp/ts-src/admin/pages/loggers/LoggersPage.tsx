import { useEffect, useMemo, useState } from "react";
import { getApiAdminLoggers } from "../../../generated/api/requests";
import type { OrgMicoliMicraftHttpLoggerLevelDto as LoggerRow } from "../../../generated/api/requests";
import { useT } from "../../i18n";

const LEVELS = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF"] as const;

export function LoggersPage() {
  const t = useT();
  const [loggers, setLoggers] = useState<LoggerRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getApiAdminLoggers({ throwOnError: true })
      .then((r) => setLoggers(r.data))
      .catch(() => setError(t("loggers.failedToLoad")))
      .finally(() => setLoading(false));
  }, [t]);

  const setLevel = async (name: string, level: string | null) => {
    setError(null);
    try {
      // {name...} is a Ktor tail parameter — the generated client's URL template for it is
      // broken (`/api/admin/loggers/{...}` with no substitution), same issue documented in
      // ConfigEditorPage for {filename...}. Kept as a manual fetch.
      const r = await fetch(`/api/admin/loggers/${encodeURIComponent(name)}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ level }),
      });
      if (!r.ok) throw new Error();
      const updated: LoggerRow = await r.json();
      setLoggers((prev) => prev.map((l) => (l.name === name ? updated : l)));
    } catch {
      setError(t("loggers.failedToSet"));
    }
  };

  const filtered = useMemo(
    () => loggers.filter((l) => l.name.toLowerCase().includes(filter.toLowerCase())),
    [loggers, filter],
  );

  return (
    <div className="h-full flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <input
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder={t("loggers.search")}
          className="flex-1 bg-[#1A222C] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] outline-none focus:border-[#3C50E0]"
        />
        {error && <span className="text-red-400 text-xs">{error}</span>}
      </div>

      <div className="flex-1 bg-[#1A222C] border border-[#2E3A4E] rounded-xl overflow-y-auto">
        {loading ? (
          <p className="px-4 py-4 text-[#4A5568] text-sm animate-pulse">{t("common.loading")}</p>
        ) : filtered.length === 0 ? (
          <p className="px-4 py-4 text-[#4A5568] text-sm">{t("loggers.empty")}</p>
        ) : (
          filtered.map((logger) => (
            <div
              key={logger.name}
              className="flex items-center gap-3 px-4 py-2 border-b border-[#2E3A4E] last:border-0"
            >
              <span className="flex-1 font-mono text-xs text-[#8A99AF] truncate" title={logger.name}>
                {logger.name || "ROOT"}
              </span>
              <span
                className={`text-[10px] font-semibold rounded px-1.5 py-0.5 ${
                  logger.level ? "bg-[#3C50E0]/20 text-[#818CF8] border border-[#3C50E0]/30" : "text-[#4A5568]"
                }`}
              >
                {logger.effectiveLevel} · {logger.level ? t("loggers.explicit") : t("loggers.inherited")}
              </span>
              <select
                value={logger.level ?? ""}
                onChange={(e) => setLevel(logger.name, e.target.value || null)}
                className="bg-[#0F141B] border border-[#2E3A4E] rounded-lg px-2 py-1 text-xs text-white outline-none focus:border-[#3C50E0]"
              >
                <option value="">{t("loggers.inherited")}</option>
                {LEVELS.map((lvl) => (
                  <option key={lvl} value={lvl}>
                    {lvl}
                  </option>
                ))}
              </select>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
