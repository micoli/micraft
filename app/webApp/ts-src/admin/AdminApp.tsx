import { BrowserRouter, Link, Route, Routes, useLocation } from "react-router";
import { cn } from "../primitives/cn";
import { ClassesPage } from "./pages/ClassesPage";
import { ConfigEditorPage } from "./pages/ConfigEditorPage";
import { PlayersPage } from "./pages/PlayersPage";
import { StatusPage } from "./pages/StatusPage";
import { UsersPage } from "./pages/UsersPage";
import { WorldsPage } from "./pages/WorldsPage";

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
  classes: "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253",
};

const NAV = [
  { path: "/admin", label: "Status", icon: ICONS.status, exact: true },
  { path: "/admin/users", label: "Users", icon: ICONS.users },
  { path: "/admin/players", label: "Players", icon: ICONS.players },
  { path: "/admin/classes", label: "Classes", icon: ICONS.classes },
  { path: "/admin/config", label: "Config", icon: ICONS.config },
  { path: "/admin/worlds", label: "Worlds", icon: ICONS.worlds },
];

const PAGE_LABELS: Record<string, string> = {
  "/admin": "Server Status",
  "/admin/users": "Users",
  "/admin/players": "Players",
  "/admin/classes": "Classes & Skills",
  "/admin/config": "Config Editor",
  "/admin/worlds": "Worlds",
};

// ── Sidebar ───────────────────────────────────────────────────────────────────
function Sidebar() {
  const { pathname } = useLocation();
  return (
    <aside className="w-64 shrink-0 h-screen flex flex-col bg-[#1C2434] border-r border-[#2E3A4E]">
      {/* Logo */}
      <div className="h-16 flex items-center px-6 border-b border-[#2E3A4E]">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-[#3C50E0] flex items-center justify-center text-white text-xs font-bold">
            MC
          </div>
          <span className="text-white font-semibold text-[15px] tracking-wide">MicCraft</span>
          <span className="text-[#8A99AF] text-xs font-normal mt-0.5">Admin</span>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-4 py-5 space-y-0.5 overflow-y-auto">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] px-3 mb-3">Menu</p>
        {NAV.map(({ path, label, icon, exact }) => {
          const active = exact ? pathname === path : pathname.startsWith(path);
          return (
            <Link
              key={path}
              to={path}
              className={cn(
                "flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150",
                active ? "bg-[#3C50E0] text-white" : "text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white",
              )}
            >
              <span className={active ? "text-white" : "text-[#8A99AF]"}>
                <Icon d={icon} size={17} />
              </span>
              {label}
            </Link>
          );
        })}
      </nav>

      <div className="px-6 py-4 border-t border-[#2E3A4E] text-[10px] text-[#8A99AF]">micraft admin v1</div>
    </aside>
  );
}

// ── Header ────────────────────────────────────────────────────────────────────
function Header() {
  const { pathname } = useLocation();
  const title = PAGE_LABELS[pathname] ?? "Admin";
  return (
    <header className="h-16 shrink-0 flex items-center justify-between px-6 bg-[#1A222C] border-b border-[#2E3A4E]">
      <div>
        <p className="text-[11px] text-[#8A99AF]">Admin / {title}</p>
        <h1 className="text-white font-semibold text-[15px] leading-tight">{title}</h1>
      </div>
      <div className="flex items-center gap-2 text-[11px] text-[#8A99AF]">
        <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" />
        Server online
      </div>
    </header>
  );
}

// ── Shell ─────────────────────────────────────────────────────────────────────
export function AdminApp() {
  return (
    <BrowserRouter>
      <div className="flex h-screen overflow-hidden bg-[#0E1726] text-white font-sans">
        <Sidebar />
        <div className="flex flex-col flex-1 overflow-hidden">
          <Header />
          <main className="flex-1 overflow-auto p-6">
            <Routes>
              <Route path="/admin" element={<StatusPage />} />
              <Route path="/admin/users" element={<UsersPage />} />
              <Route path="/admin/players" element={<PlayersPage />} />
              <Route path="/admin/classes" element={<ClassesPage />} />
              <Route path="/admin/config" element={<ConfigEditorPage />} />
              <Route path="/admin/worlds" element={<WorldsPage />} />
            </Routes>
          </main>
        </div>
      </div>
    </BrowserRouter>
  );
}
