import { Navigate, useNavigate, useParams } from "react-router";
import { useT, type TranslationKey } from "../../i18n";
import { GroupsTab } from "./GroupsTab";
import { GuildsTab } from "./GuildsTab";
import { FactionsTab } from "./FactionsTab";

type SocialTab = "groups" | "guilds" | "factions";

const TAB_LABEL_KEYS: Record<SocialTab, TranslationKey> = {
  groups: "administration.tabGroups",
  guilds: "administration.tabGuilds",
  factions: "administration.tabFactions",
};

const DEFAULT_TAB: SocialTab = "guilds";

export function SocialPage() {
  const t = useT();
  const navigate = useNavigate();
  const { tab, id } = useParams<{ tab?: string; id?: string }>();

  if (!tab || !(tab in TAB_LABEL_KEYS)) {
    return <Navigate to={`/admin/social/${DEFAULT_TAB}`} replace />;
  }
  const activeTab = tab as SocialTab;

  const selectId = (next: string | null) =>
    navigate(next ? `/admin/social/${activeTab}/${encodeURIComponent(next)}` : `/admin/social/${activeTab}`);

  return (
    <div className="flex flex-col h-full overflow-hidden -m-6">
      <div className="shrink-0 flex border-b border-[#2E3A4E] px-6 bg-[#1A222C]">
        {(Object.keys(TAB_LABEL_KEYS) as SocialTab[]).map((key) => (
          <button
            key={key}
            onClick={() => navigate(`/admin/social/${key}`)}
            className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
              activeTab === key ? "border-[#3C50E0] text-white" : "border-transparent text-[#8A99AF] hover:text-white"
            }`}
          >
            {t(TAB_LABEL_KEYS[key])}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-hidden">
        {activeTab === "groups" && <GroupsTab selectedId={id ?? null} onSelect={selectId} />}
        {activeTab === "guilds" && <GuildsTab selectedId={id ?? null} onSelect={selectId} />}
        {activeTab === "factions" && <FactionsTab selectedId={id ?? null} onSelect={selectId} />}
      </div>
    </div>
  );
}
