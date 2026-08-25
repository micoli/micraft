import { Navigate, useNavigate, useParams } from "react-router";
import { useT, type TranslationKey } from "../../i18n";
import { BlocksTab } from "./BlocksTab";
import { ItemsTab } from "./ItemsTab";
import { BestiaryTab } from "./BestiaryTab";
import { ModelsTab } from "./ModelsTab";
import { EquipmentTab } from "./EquipmentTab";
import { SiegeWeaponsTab } from "./SiegeWeaponsTab";

type AdminTab = "blocks" | "items" | "bestiary" | "models" | "equipment" | "siegeWeapons";

const TAB_LABEL_KEYS: Record<AdminTab, TranslationKey> = {
  models: "administration.tabModels",
  blocks: "administration.tabBlocks",
  items: "administration.tabItems",
  bestiary: "administration.tabBestiary",
  equipment: "administration.tabEquipment",
  siegeWeapons: "administration.tabSiegeWeapons",
};

const DEFAULT_TAB: AdminTab = "models";

export function CodexPage() {
  const t = useT();
  const navigate = useNavigate();
  const { tab, itemKey } = useParams<{ tab: string; itemKey?: string }>();

  if (!tab || !(tab in TAB_LABEL_KEYS)) {
    return <Navigate to={`/admin/codex/${DEFAULT_TAB}`} replace />;
  }
  const activeTab = tab as AdminTab;

  const selectItem = (key: string | null, options?: { replace?: boolean }) =>
    navigate(key ? `/admin/codex/${activeTab}/${encodeURIComponent(key)}` : `/admin/codex/${activeTab}`, options);

  return (
    <div className="flex flex-col h-full overflow-hidden -m-6">
      {/* Tab bar */}
      <div className="shrink-0 flex border-b border-[#2E3A4E] px-6 bg-[#1A222C]">
        {(Object.keys(TAB_LABEL_KEYS) as AdminTab[]).map((key) => (
          <button
            key={key}
            onClick={() => navigate(`/admin/codex/${key}`)}
            className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
              activeTab === key ? "border-[#3C50E0] text-white" : "border-transparent text-[#8A99AF] hover:text-white"
            }`}
          >
            {t(TAB_LABEL_KEYS[key])}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-hidden">
        {activeTab === "models" && (
          <ModelsTab selectedKey={itemKey ? decodeURIComponent(itemKey) : null} onSelectKey={selectItem} />
        )}
        {activeTab === "blocks" && (
          <BlocksTab selectedKey={itemKey ? decodeURIComponent(itemKey) : null} onSelectKey={selectItem} />
        )}
        {activeTab === "items" && (
          <ItemsTab selectedKey={itemKey ? decodeURIComponent(itemKey) : null} onSelectKey={selectItem} />
        )}
        {activeTab === "bestiary" && (
          <BestiaryTab selectedKey={itemKey ? decodeURIComponent(itemKey) : null} onSelectKey={selectItem} />
        )}
        {activeTab === "equipment" && (
          <EquipmentTab selectedKey={itemKey ? decodeURIComponent(itemKey) : null} onSelectKey={selectItem} />
        )}
        {activeTab === "siegeWeapons" && (
          <SiegeWeaponsTab selectedKey={itemKey ? decodeURIComponent(itemKey) : null} onSelectKey={selectItem} />
        )}
      </div>
    </div>
  );
}
