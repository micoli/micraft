import { useT } from "./i18n";
import { useTabs } from "./TabsContext";
import { Icon } from "../primitives/Icon";
import { cn } from "../primitives/cn";

const REFRESH_ICON_D =
  "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15";
const CLOSE_ICON_D = "M6 18L18 6M6 6l12 12";

export function TabBar() {
  const { tabs, activeTab, activateTab, closeTab, refreshTab } = useTabs();
  const t = useT();

  if (tabs.length === 0) return null;

  return (
    <div className="flex items-end gap-1 px-3 pt-2 bg-[#1A222C] border-b border-[#2E3A4E] overflow-x-auto">
      {tabs.map((tab) => {
        const active = tab.path === activeTab?.path;
        return (
          <div
            key={tab.path}
            onClick={() => activateTab(tab.path)}
            role="tab"
            aria-selected={active}
            className={cn(
              "group flex items-center gap-1.5 pl-3 pr-1.5 py-1.5 rounded-t-md text-sm cursor-pointer select-none",
              active ? "bg-[#0E1726] text-white" : "bg-[#151C29] text-[#8A99AF] hover:bg-[#1C2434] hover:text-white",
            )}
          >
            <span className="whitespace-nowrap">{t(tab.labelKey)}</span>
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                refreshTab(tab.path);
              }}
              title={t("shell.refreshTab")}
              aria-label={t("shell.refreshTab")}
              className="flex h-5 w-5 shrink-0 items-center justify-center rounded hover:bg-[#2E3A4E] text-[#8A99AF] hover:text-white"
            >
              <Icon d={REFRESH_ICON_D} size={12} />
            </button>
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                closeTab(tab.path);
              }}
              title={t("shell.closeTab")}
              aria-label={t("shell.closeTab")}
              className="flex h-5 w-5 shrink-0 items-center justify-center rounded hover:bg-[#2E3A4E] text-[#8A99AF] hover:text-white"
            >
              <Icon d={CLOSE_ICON_D} size={12} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
