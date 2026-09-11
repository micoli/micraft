import { Link, Route, Routes, useLocation, useNavigate } from "react-router";
import { Button } from "../primitives/Button";
import { cn } from "../primitives/cn";
import { hubLogout } from "./auth/HubAuthGate";
import { HubStatusBanner } from "./HubStatusBanner";
import { useHubSocket } from "./state/HubSocketProvider";
import { LOCALE_LABELS, LOCALES, useI18n } from "./i18n";
import { MailRoute } from "./routes/MailRoute";
import { AuctionRoute } from "./routes/AuctionRoute";
import { ChatRoute } from "./routes/ChatRoute";
import { ClaimsRoute } from "./routes/ClaimsRoute";

const NAV_ITEMS = [
  { path: "/hub/mail", labelKey: "shell.nav.mail" as const },
  { path: "/hub/auction", labelKey: "shell.nav.auction" as const },
  { path: "/hub/chat", labelKey: "shell.nav.chat" as const },
  { path: "/hub/claims", labelKey: "shell.nav.claims" as const },
];

/**
 * Same skin as `admin/` (`AdminShell.tsx`/`SidebarComponent.tsx`/`Header.tsx`) — same palette,
 * same sidebar/header shape — so the two companion apps read as one family rather than two
 * one-off designs. No tab system (admin's `TabsProvider`) since the hub only ever has one screen
 * open at a time; react-router's own history covers back/forward.
 */
export function HubShell() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { locale, setLocale, t } = useI18n();
  const { playerName } = useHubSocket();
  const activeLabel = t(NAV_ITEMS.find((item) => pathname.startsWith(item.path))?.labelKey ?? "shell.title");

  return (
    <div className="flex h-screen overflow-hidden bg-[#0E1726] text-white font-sans">
      <aside className="w-64 shrink-0 h-screen flex flex-col bg-[#1C2434] border-r border-[#2E3A4E]">
        <div className="h-16 flex items-center border-b border-[#2E3A4E] px-6">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="w-8 h-8 shrink-0 rounded-lg bg-[#3C50E0] flex items-center justify-center text-white text-xs font-bold">
              MC
            </div>
            <span className="text-white font-semibold text-[15px] tracking-wide">MiCraft</span>
            <span className="text-[#8A99AF] text-xs font-normal mt-0.5">Hub</span>
          </div>
        </div>
        <nav className="flex-1 py-5 px-4 space-y-0.5 overflow-y-auto">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] px-3 mb-3">
            {t("shell.title")}
          </p>
          {NAV_ITEMS.map((item) => {
            const active = pathname.startsWith(item.path);
            return (
              <Link
                key={item.path}
                to={item.path}
                className={cn(
                  "flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150",
                  active ? "bg-[#3C50E0] text-white" : "text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white",
                )}
              >
                {t(item.labelKey)}
              </Link>
            );
          })}
        </nav>
        <div className="border-t border-[#2E3A4E] px-6 py-3 flex items-center gap-2 text-[10px] text-[#8A99AF]">
          <select
            value={locale}
            onChange={(e) => setLocale(e.target.value as (typeof LOCALES)[number])}
            className="flex-1 bg-transparent border border-[#2E3A4E] rounded px-2 py-1 text-[11px] text-[#C7D2FE]"
            aria-label={t("shell.language")}
          >
            {LOCALES.map((l) => (
              <option key={l} value={l} className="text-black">
                {LOCALE_LABELS[l]}
              </option>
            ))}
          </select>
        </div>
      </aside>
      <div className="flex flex-col flex-1 overflow-hidden">
        <header className="h-16 shrink-0 flex items-center justify-between px-6 bg-[#1A222C] border-b border-[#2E3A4E]">
          <div>
            <p className="text-[11px] text-[#8A99AF]">
              {t("shell.title")} / {activeLabel}
            </p>
            <h1 className="text-white font-semibold text-[15px] leading-tight">{playerName}</h1>
          </div>
          <Button size="sm" variant="ghost" onClick={() => hubLogout(navigate)}>
            {t("shell.logout")}
          </Button>
        </header>
        <HubStatusBanner />
        <main className="flex-1 overflow-auto p-6">
          {/* h-full: every route below fills this padded box rather than floating over it — see
              each route's `chrome="page"` (MailboxOverlay/AuctionHouse/ClaimPanel) doc. */}
          <div className="h-full">
            <Routes>
              {/* Relative to the parent "/hub/*" route this shell is mounted under (element=, no
                  Outlet) — react-router scopes a nested <Routes> to the remaining path in that
                  case, so these must NOT repeat the "/hub" prefix. */}
              <Route path="mail" element={<MailRoute />} />
              <Route path="auction" element={<AuctionRoute />} />
              <Route path="chat" element={<ChatRoute />} />
              <Route path="claims" element={<ClaimsRoute />} />
            </Routes>
          </div>
        </main>
      </div>
    </div>
  );
}
