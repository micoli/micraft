import { useEffect, useState } from "react";
import { BrowserRouter, Link, Route, Routes, useLocation } from "react-router";
import { cn } from "../primitives/cn";
import { LanguageSelector } from "./components/LanguageSelector";
import { I18nProvider, useT, type TranslationKey } from "./i18n";
import { loadSidebarCollapsed, saveSidebarCollapsed } from "./sidebar";
import { ClassesPage } from "./pages/ClassesPage";
import { ConfigEditorPage } from "./pages/ConfigEditorPage";
import { NpcsPage } from "./pages/NpcsPage";
import { PlayersPage } from "./pages/PlayersPage";
import { StatusPage } from "./pages/StatusPage";
import { UsersPage } from "./pages/UsersPage";
import { WorldsPage } from "./pages/WorldsPage";
import { GameAssetsPage } from "./pages/GameAssetsPage";
import { AdministrationPage } from "./pages/AdministrationPage";
import { WorldSimulatorPage } from "./pages/WorldSimulatorPage";
import { InstancesPage } from "./pages/instance/InstancesPage";

// ── Icons ────────────────────────────────────────────────────────────────────
function Icon({ d, size = 18 }: { d: string; size?: number }) {
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

const ICONS = {
  status:
    "M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z",
  users: "M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zm8 2l2 2 4-4",
  players: "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z",
  config:
    "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z",
  worlds: "M3 7l9-4 9 4M3 7v10l9 4m-9-14l9 4m9-4v10l-9 4m0-14v14",
  classes:
    "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253",
  npcs: "M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18",
  gameAssets: "M21 7.5l-9-5.25L3 7.5m18 0l-9 5.25m9-5.25v9l-9 5.25M3 7.5l9 5.25M3 7.5v9l9 5.25m0-9v9",
  administration: "M4 6h16M4 10h16M4 14h8M4 18h8",
  simulator: "M4 4h16v16H4zM8 8v8m8-8v8m-4-6a2 2 0 100 4 2 2 0 000-4z",
  instances: "M12 2l9 4.5v9L12 20l-9-4.5v-9L12 2zM12 2v18M3 6.5l9 4.5 9-4.5",
};

const NAV: { path: string; labelKey: TranslationKey; icon: string; exact?: boolean }[] = [
  { path: "/admin", labelKey: "nav.status", icon: ICONS.status, exact: true },
  { path: "/admin/users", labelKey: "nav.users", icon: ICONS.users },
  { path: "/admin/players", labelKey: "nav.players", icon: ICONS.players },
  { path: "/admin/npcs", labelKey: "nav.npcs", icon: ICONS.npcs },
  { path: "/admin/classes", labelKey: "nav.classes", icon: ICONS.classes },
  { path: "/admin/config", labelKey: "nav.config", icon: ICONS.config },
  { path: "/admin/worlds", labelKey: "nav.worlds", icon: ICONS.worlds },
  { path: "/admin/game-assets", labelKey: "nav.gameAssets", icon: ICONS.gameAssets },
  { path: "/admin/administration", labelKey: "nav.administration", icon: ICONS.administration },
  { path: "/admin/instances", labelKey: "nav.instances", icon: ICONS.instances },
  { path: "/admin/world-simulator", labelKey: "nav.worldSimulator", icon: ICONS.simulator },
];

const PAGE_LABEL_KEYS: Record<string, TranslationKey> = {
  "/admin": "page.status",
  "/admin/users": "page.users",
  "/admin/players": "page.players",
  "/admin/npcs": "page.npcs",
  "/admin/classes": "page.classes",
  "/admin/config": "page.config",
  "/admin/worlds": "page.worlds",
  "/admin/game-assets": "page.gameAssets",
  "/admin/administration": "page.administration",
  "/admin/instances": "page.instances",
  "/admin/world-simulator": "page.worldSimulator",
};

// ── Sidebar ───────────────────────────────────────────────────────────────────
function Sidebar({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const { pathname } = useLocation();
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

// ── Header ────────────────────────────────────────────────────────────────────
function Header() {
  const { pathname } = useLocation();
  const t = useT();
  const labelKey =
    PAGE_LABEL_KEYS[pathname] ??
    Object.entries(PAGE_LABEL_KEYS).find(([path]) => path !== "/admin" && pathname.startsWith(path))?.[1];
  const title = labelKey ? t(labelKey) : t("shell.admin");
  return (
    <header className="h-16 shrink-0 flex items-center justify-between px-6 bg-[#1A222C] border-b border-[#2E3A4E]">
      <div>
        <p className="text-[11px] text-[#8A99AF]">
          {t("shell.admin")} / {title}
        </p>
        <h1 className="text-white font-semibold text-[15px] leading-tight">{title}</h1>
      </div>
      <div className="flex items-center gap-2 text-[11px] text-[#8A99AF]">
        <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" />
        {t("shell.serverOnline")}
      </div>
    </header>
  );
}

// ── Shell ─────────────────────────────────────────────────────────────────────
export function AdminApp() {
  const [collapsed, setCollapsed] = useState(() => loadSidebarCollapsed());

  useEffect(() => saveSidebarCollapsed(collapsed), [collapsed]);

  return (
    <I18nProvider>
      <BrowserRouter>
        <div className="flex h-screen overflow-hidden bg-[#0E1726] text-white font-sans">
          <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((current) => !current)} />
          <div className="flex flex-col flex-1 overflow-hidden">
            <Header />
            <main className="flex-1 overflow-auto p-6">
              <Routes>
                <Route path="/admin" element={<StatusPage />} />
                <Route path="/admin/users" element={<UsersPage />} />
                <Route path="/admin/players" element={<PlayersPage />} />
                <Route path="/admin/npcs" element={<NpcsPage />} />
                <Route path="/admin/classes" element={<ClassesPage />} />
                <Route path="/admin/config" element={<ConfigEditorPage />} />
                <Route path="/admin/worlds" element={<WorldsPage />} />
                <Route path="/admin/game-assets" element={<GameAssetsPage />} />
                <Route path="/admin/administration" element={<AdministrationPage />} />
                <Route path="/admin/instances" element={<InstancesPage />} />
                <Route path="/admin/instances/:id" element={<InstancesPage />} />
                <Route path="/admin/world-simulator" element={<WorldSimulatorPage />} />
              </Routes>
            </main>
          </div>
        </div>
      </BrowserRouter>
    </I18nProvider>
  );
}
