import { Link, useLocation } from "react-router";
import { useT } from "./i18n";
import { cn } from "../primitives/cn";
import { LanguageSelector } from "./components/LanguageSelector";
import { Icon } from "../primitives/Icon";
import { NAV } from "./const";
import { useTabs } from "./TabsContext";

export function SidebarComponent({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const { pathname } = useLocation();
  const { activateTab } = useTabs();
  const t = useT();
  return (
    <aside
      className={cn(
        "shrink-0 h-screen flex flex-col bg-[#1C2434] border-r border-[#2E3A4E] transition-[width] duration-150",
        collapsed ? "w-16" : "w-64",
      )}
    >
      {/* Logo */}
      <div className={cn("h-16 flex items-center border-b border-[#2E3A4E]", collapsed ? "px-3" : "px-6")}>
        <div className="flex items-center gap-2.5 overflow-hidden">
          <div className="w-8 h-8 shrink-0 rounded-lg bg-[#3C50E0] flex items-center justify-center text-white text-xs font-bold">
            MC
          </div>
          {!collapsed && (
            <>
              <span className="text-white font-semibold text-[15px] tracking-wide">MicCraft</span>
              <span className="text-[#8A99AF] text-xs font-normal mt-0.5">Admin</span>
            </>
          )}
        </div>
      </div>

      {/* Nav */}
      <nav className={cn("flex-1 py-5 space-y-0.5 overflow-y-auto", collapsed ? "px-2" : "px-4")}>
        {!collapsed && (
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] px-3 mb-3">
            {t("shell.menu")}
          </p>
        )}
        {NAV.map(({ path, labelKey, icon, exact }) => {
          const active = exact ? pathname === path : pathname.startsWith(path);
          const label = t(labelKey);
          return (
            <Link
              key={path}
              to={path}
              onClick={(event) => {
                // reopening an already-open tab must land back where it was, not reset to its base path
                event.preventDefault();
                activateTab(path);
              }}
              // the label is the only thing that goes away, so it becomes the tooltip: a column of
              // unlabelled icons is otherwise a memory test
              title={collapsed ? label : undefined}
              aria-label={label}
              className={cn(
                "flex items-center gap-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150",
                collapsed ? "justify-center px-0" : "px-3",
                active ? "bg-[#3C50E0] text-white" : "text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white",
              )}
            >
              <span className={active ? "text-white" : "text-[#8A99AF]"}>
                <Icon d={icon} size={17} />
              </span>
              {!collapsed && label}
            </Link>
          );
        })}
      </nav>

      <LanguageSelector collapsed={collapsed} />

      <div
        className={cn(
          "border-t border-[#2E3A4E] flex items-center gap-2 py-3 text-[10px] text-[#8A99AF]",
          collapsed ? "px-2 justify-center" : "px-6",
        )}
      >
        {!collapsed && <span className="flex-1">{t("shell.version")}</span>}
        <button
          type="button"
          onClick={onToggle}
          title={t(collapsed ? "shell.expandMenu" : "shell.collapseMenu")}
          aria-label={t(collapsed ? "shell.expandMenu" : "shell.collapseMenu")}
          aria-expanded={!collapsed}
          className="flex h-7 w-7 items-center justify-center rounded border border-[#2E3A4E] text-[#C7D2FE] hover:bg-[#3C50E0]/60 hover:text-white"
        >
          <Icon d={collapsed ? "M9 5l7 7-7 7" : "M15 19l-7-7 7-7"} size={15} />
        </button>
      </div>
    </aside>
  );
}
