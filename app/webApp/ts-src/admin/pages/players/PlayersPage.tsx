import { useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { getApiAdminPlayers } from "../../../generated/api/requests";
import { useT } from "../../i18n";
import { PlayerDetail } from "./PlayerDetail";
import { EmptyDetail } from "../../../primitives/EmptyDetail";

const LIST_COLLAPSED_STORAGE_KEY = "adminPlayersListCollapsed";

export function PlayersPage() {
  const t = useT();
  const navigate = useNavigate();
  const { playerName } = useParams();
  const [searchParams] = useSearchParams();
  const [players, setPlayers] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const [listCollapsed, setListCollapsed] = useState(() => localStorage.getItem(LIST_COLLAPSED_STORAGE_KEY) === "1");
  const selected = playerName ?? searchParams.get("p");
  const filteredPlayers = players.filter((p) => p.toLowerCase().includes(filter.toLowerCase()));

  useEffect(() => {
    localStorage.setItem(LIST_COLLAPSED_STORAGE_KEY, listCollapsed ? "1" : "0");
  }, [listCollapsed]);

  useEffect(() => {
    getApiAdminPlayers({ throwOnError: true }).then((r) => {
      setPlayers(r.data.sort());
      setLoading(false);
    });
  }, []);

  const handleRenamed = (oldName: string, newName: string) => {
    setPlayers((prev) => prev.map((p) => (p === oldName ? newName : p)).sort());
    navigate(`/admin/players/${encodeURIComponent(newName)}`);
  };

  return (
    <div className="flex h-full overflow-hidden -m-6">
      <aside
        className={`${listCollapsed ? "w-8" : "w-56"} shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden transition-[width]`}
      >
        <div className="px-3 py-2 border-b border-[#2E3A4E] flex items-center justify-between">
          {!listCollapsed && (
            <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">
              {t("players.title")}
            </span>
          )}
          <button
            className="text-[#8A99AF] hover:text-white text-xs shrink-0"
            title={listCollapsed ? "Expand player list" : "Collapse player list"}
            onClick={() => setListCollapsed((c) => !c)}
          >
            {listCollapsed ? "»" : "«"}
          </button>
        </div>
        {!listCollapsed && (
          <>
            <div className="px-3 py-2 border-b border-[#2E3A4E]">
              <input
                type="text"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                placeholder={t("players.filter")}
                className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded-md px-2 py-1.5 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0]"
              />
            </div>
            <div className="flex-1 overflow-y-auto py-2">
              {loading ? (
                <p className="px-3 py-2 text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>
              ) : filteredPlayers.length === 0 ? (
                <p className="px-3 py-2 text-[#4A5568] text-sm">{t("players.none")}</p>
              ) : (
                filteredPlayers.map((p) => (
                  <button
                    key={p}
                    onClick={() => navigate(`/admin/players/${encodeURIComponent(p)}`)}
                    className={`w-full text-left px-3 py-2 text-sm truncate transition-colors ${
                      selected === p
                        ? "bg-[#3C50E0]/20 text-white"
                        : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
                    }`}
                  >
                    {p}
                  </button>
                ))
              )}
            </div>
          </>
        )}
      </aside>

      <div className="flex-1 flex flex-col overflow-hidden">
        {!selected && <EmptyDetail message={t("players.selectToEdit")} />}
        {selected && (
          <PlayerDetail
            key={selected}
            name={selected}
            onBack={() => navigate("/admin/players")}
            onRenamed={(newName) => handleRenamed(selected, newName)}
          />
        )}
      </div>
    </div>
  );
}
