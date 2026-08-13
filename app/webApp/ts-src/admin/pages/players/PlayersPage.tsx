import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { getApiAdminPlayers } from "../../../generated/api/requests";
import { useT } from "../../i18n";
import { PlayerDetail } from "./PlayerDetail";

export function PlayersPage() {
  const t = useT();
  const [searchParams] = useSearchParams();
  const [players, setPlayers] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<string | null>(searchParams.get("p"));

  useEffect(() => {
    getApiAdminPlayers({ throwOnError: true }).then((r) => {
      setPlayers(r.data.sort());
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
          {t("players.title")}
        </p>
        {loading ? (
          <p className="px-4 py-4 text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>
        ) : players.length === 0 ? (
          <p className="px-4 py-4 text-[#4A5568] text-sm">{t("players.none")}</p>
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
          <div className="p-6 text-[#4A5568] text-sm">{t("players.selectToEdit")}</div>
        )}
      </div>
    </div>
  );
}
